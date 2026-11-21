package dev.triaxis.assistant.infrastructure.adapter.out.llm;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.domain.exception.AssistantUnavailableException;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

/**
 * Decorator of the provider (ADR 0004): every call has a timeout, goes through the circuit breaker {@code llm}
 * and is timed in {@code assistant.llm.latency} (tags {@code operation} and {@code outcome}). Any failure, a
 * timeout or an open breaker becomes {@link AssistantUnavailableException}, which the web layer answers with a
 * 503. The bulkhead {@code llm} bounds the calls in flight, so a burst of questions cannot spend the quota of the
 * provider at once: a call over the bound is refused at once with the same 503 (ADR 0025), and it does not count
 * as a failure of the breaker. An answer that is not the expected JSON is not a failure: it is a successful call whose content the
 * citation check rejects.
 */
public class ResilientLlmAdapter implements LlmPort {

	private final LlmPort provider;
	private final CircuitBreaker breaker;
	private final Bulkhead bulkhead;
	private final MeterRegistry meters;
	private final Duration embeddingTimeout;
	private final Duration answerTimeout;

	public ResilientLlmAdapter(LlmPort provider, CircuitBreaker breaker, Bulkhead bulkhead, MeterRegistry meters,
			Duration embeddingTimeout, Duration answerTimeout) {
		this.provider = provider;
		this.breaker = breaker;
		this.bulkhead = bulkhead;
		this.meters = meters;
		this.embeddingTimeout = embeddingTimeout;
		this.answerTimeout = answerTimeout;
	}

	@Override
	public String embeddingModel() {
		return provider.embeddingModel();
	}

	@Override
	public Mono<float[]> embed(String text) {
		return protect("embedding", Mono.defer(() -> provider.embed(text)), embeddingTimeout);
	}

	@Override
	public Mono<ModelAnswer> answer(String question, List<NumberedExcerpt> excerpts) {
		return protect("answer", Mono.defer(() -> provider.answer(question, excerpts)), answerTimeout);
	}

	private <T> Mono<T> protect(String operation, Mono<T> call, Duration timeout) {
		return Mono.defer(() -> {
			long start = System.nanoTime();
			return call.timeout(timeout)
					.transformDeferred(CircuitBreakerOperator.of(breaker))
					.transformDeferred(BulkheadOperator.of(bulkhead))
					.doOnSuccess(result -> record(operation, "success", start))
					.doOnError(error -> record(operation, error instanceof CallNotPermittedException
							|| error instanceof BulkheadFullException ? "rejected" : "failure", start))
					.onErrorMap(error -> !(error instanceof AssistantUnavailableException), ResilientLlmAdapter::unavailable);
		});
	}

	private void record(String operation, String outcome, long start) {
		Timer.builder("assistant.llm.latency")
				.description("Time of the calls to the language model provider")
				.tag("operation", operation)
				.tag("outcome", outcome)
				.register(meters)
				.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
	}

	private static AssistantUnavailableException unavailable(Throwable error) {
		if (error instanceof CallNotPermittedException) {
			return new AssistantUnavailableException("The language model is unavailable (circuit open)", error);
		}
		if (error instanceof BulkheadFullException) {
			return new AssistantUnavailableException("The language model is busy (too many calls in flight)", error);
		}
		if (error instanceof TimeoutException) {
			return new AssistantUnavailableException("The language model did not answer in time", error);
		}
		return new AssistantUnavailableException("The language model call failed", error);
	}

}
