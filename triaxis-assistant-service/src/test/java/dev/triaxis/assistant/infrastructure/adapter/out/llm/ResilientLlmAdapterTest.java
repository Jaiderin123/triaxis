package dev.triaxis.assistant.infrastructure.adapter.out.llm;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.domain.exception.AssistantUnavailableException;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientLlmAdapterTest {

	/** A provider whose next result is chosen by the test, counting how often it is really called. */
	private static final class ScriptedProvider implements LlmPort {

		final AtomicInteger calls = new AtomicInteger();
		Mono<ModelAnswer> answer = Mono.just(ModelAnswer.noProtocolFound());
		Mono<float[]> embedding = Mono.just(new float[] {1f});

		@Override
		public String embeddingModel() {
			return "scripted";
		}

		@Override
		public Mono<float[]> embed(String text) {
			return Mono.defer(() -> {
				calls.incrementAndGet();
				return embedding;
			});
		}

		@Override
		public Mono<ModelAnswer> answer(String question, List<NumberedExcerpt> excerpts) {
			return Mono.defer(() -> {
				calls.incrementAndGet();
				return answer;
			});
		}

	}

	private final ScriptedProvider provider = new ScriptedProvider();
	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final CircuitBreaker breaker = CircuitBreaker.of("llm", CircuitBreakerConfig.custom()
			.slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
			.waitDurationInOpenState(Duration.ofMinutes(1)).build());
	private final Bulkhead bulkhead = Bulkhead.of("llm", BulkheadConfig.custom().maxConcurrentCalls(2)
			.maxWaitDuration(Duration.ZERO).build());
	private final ResilientLlmAdapter llm = new ResilientLlmAdapter(provider, breaker, bulkhead, meters,
			Duration.ofMillis(200), Duration.ofMillis(200));

	@Test
	void aSuccessfulCallPassesThroughAndIsTimed() {
		StepVerifier.create(llm.answer("q", List.of())).expectNext(ModelAnswer.noProtocolFound()).verifyComplete();

		assertThat(meters.get("assistant.llm.latency").tag("operation", "answer").tag("outcome", "success").timer()
				.count()).isEqualTo(1);
		assertThat(llm.embeddingModel()).isEqualTo("scripted");
	}

	@Test
	void aProviderFailureBecomesUnavailable() {
		provider.embedding = Mono.error(new IllegalStateException("HTTP 503 from provider"));

		StepVerifier.create(llm.embed("texto"))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AssistantUnavailableException.class)
						.hasMessage("The language model call failed")
						.hasRootCauseMessage("HTTP 503 from provider"))
				.verify();
		assertThat(meters.get("assistant.llm.latency").tag("outcome", "failure").timer().count()).isEqualTo(1);
	}

	@Test
	void aSlowCallTimesOutAndCountsAsAFailure() {
		provider.answer = Mono.never();

		StepVerifier.create(llm.answer("q", List.of()))
				.expectErrorMatches(error -> error instanceof AssistantUnavailableException
						&& error.getMessage().contains("in time"))
				.verify(Duration.ofSeconds(5));
		assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
	}

	@Test
	void afterEnoughFailuresTheBreakerOpensAndTheProviderIsNotCalledAnymore() {
		provider.answer = Mono.error(new IllegalStateException("HTTP 500"));
		for (int i = 0; i < 4; i++) {
			StepVerifier.create(llm.answer("q", List.of())).expectError(AssistantUnavailableException.class).verify();
		}
		assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
		int callsBefore = provider.calls.get();

		StepVerifier.create(llm.answer("q", List.of()))
				.expectErrorMatches(error -> error instanceof AssistantUnavailableException
						&& error.getMessage().contains("circuit open"))
				.verify();
		assertThat(provider.calls.get()).isEqualTo(callsBefore);
		assertThat(meters.get("assistant.llm.latency").tag("outcome", "rejected").timer().count()).isEqualTo(1);
	}

	/** A burst over the bound of calls in flight is refused at once, without calling the provider (ADR 0025). */
	@Test
	void aCallOverTheBoundInFlightIsRefusedWithoutCallingTheProviderOrOpeningTheBreaker() {
		Sinks.One<ModelAnswer> pending = Sinks.one();
		provider.answer = pending.asMono();
		ResilientLlmAdapter slow = new ResilientLlmAdapter(provider, breaker, bulkhead, meters,
				Duration.ofSeconds(5), Duration.ofSeconds(5));
		var first = slow.answer("q", List.of()).subscribe();
		var second = slow.answer("q", List.of()).subscribe();
		int callsInFlight = provider.calls.get();

		StepVerifier.create(slow.answer("q", List.of()))
				.expectErrorMatches(error -> error instanceof AssistantUnavailableException
						&& error.getMessage().contains("busy"))
				.verify();

		assertThat(callsInFlight).isEqualTo(2);
		assertThat(provider.calls.get()).isEqualTo(2);
		assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
		pending.tryEmitValue(ModelAnswer.noProtocolFound());
		first.dispose();
		second.dispose();
		StepVerifier.create(slow.answer("q", List.of())).expectNext(ModelAnswer.noProtocolFound()).verifyComplete();
	}

	@Test
	void anUnreadableAnswerIsNotAFailureOfTheProvider() {
		provider.answer = Mono.just(ModelAnswer.unusable());

		for (int i = 0; i < 4; i++) {
			StepVerifier.create(llm.answer("q", List.of())).expectNext(ModelAnswer.unusable()).verifyComplete();
		}
		assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

}
