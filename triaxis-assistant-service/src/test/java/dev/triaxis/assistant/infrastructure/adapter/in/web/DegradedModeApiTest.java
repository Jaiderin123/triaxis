package dev.triaxis.assistant.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.assistant.TestSecurityConfiguration;
import dev.triaxis.assistant.TestcontainersConfiguration;
import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.application.service.CorpusState;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.mock.MockLlmAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static dev.triaxis.assistant.TestSecurityConfiguration.token;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Degraded mode (ADR 0004) with a provider whose embeddings work but whose answers always fail: every question
 * is a 503, the breaker opens after five failures and then the provider is not called anymore. Its own context,
 * because the provider is replaced.
 */
@SpringBootTest(properties = {"assistant.llm.provider=failing-test-provider",
		"resilience4j.ratelimiter.configs.question.limit-for-period=100000", "spring.test.webtestclient.timeout=30s"})
@AutoConfigureWebTestClient
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class, DegradedModeApiTest.FailingProvider.class})
class DegradedModeApiTest {

	static final AtomicInteger ANSWER_CALLS = new AtomicInteger();

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingProvider {

		@Bean("llmProvider")
		LlmPort failingProvider() {
			MockLlmAdapter embeddings = new MockLlmAdapter();
			return new LlmPort() {
				@Override
				public String embeddingModel() {
					return embeddings.embeddingModel();
				}

				@Override
				public Mono<float[]> embed(String text) {
					return embeddings.embed(text);
				}

				@Override
				public Mono<ModelAnswer> answer(String question, List<NumberedExcerpt> excerpts) {
					return Mono.defer(() -> {
						ANSWER_CALLS.incrementAndGet();
						return Mono.error(new IllegalStateException("HTTP 503 from the provider"));
					});
				}
			};
		}

	}

	@Autowired
	WebTestClient client;

	@Autowired
	CorpusState corpus;

	@Autowired
	CircuitBreakerRegistry breakers;

	@Autowired
	MeterRegistry meters;

	private WebTestClient.ResponseSpec ask(String question) {
		return client.post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("doctor-luis", "DOCTOR"))
				.bodyValue(Map.of("pregunta", question)).exchange();
	}

	/** Both tests share the breaker of the context: each one starts with it closed, whatever the order. */
	@BeforeEach
	void closeTheBreaker() {
		breakers.circuitBreaker("llm").reset();
	}

	@Test
	void anUnavailableProviderGivesA503AndTheBreakerStopsCallingIt() throws InterruptedException {
		for (int i = 0; i < 400 && !corpus.isReady(); i++) {
			Thread.sleep(50);
		}
		String covered = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";

		for (int i = 0; i < 5; i++) {
			ask(covered).expectStatus().isEqualTo(503)
					.expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "30")
					.expectBody()
					.jsonPath("$.detail").isEqualTo("The clinical assistant is temporarily unavailable")
					.jsonPath("$.respuesta").doesNotExist();
		}
		assertThat(breakers.circuitBreaker("llm").getState()).isEqualTo(CircuitBreaker.State.OPEN);
		int callsWhenOpen = ANSWER_CALLS.get();

		ask(covered).expectStatus().isEqualTo(503);

		assertThat(ANSWER_CALLS.get()).isEqualTo(callsWhenOpen);
		assertThat(meters.counter("assistant.answers", "outcome", "degraded").count()).isGreaterThanOrEqualTo(6);
	}

	@Test
	void aQuestionWithoutProtocolGetsTheNoticeWithoutCallingTheChatModel() throws InterruptedException {
		for (int i = 0; i < 400 && !corpus.isReady(); i++) {
			Thread.sleep(50);
		}
		int before = ANSWER_CALLS.get();

		ask("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?").expectStatus().isOk()
				.expectBody().jsonPath("$.fuentes.length()").isEqualTo(0);
		assertThat(ANSWER_CALLS.get()).isEqualTo(before);
	}

}
