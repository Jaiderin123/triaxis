package dev.triaxis.assistant.infrastructure.adapter.in.web;

import java.time.Duration;
import java.util.Map;

import dev.triaxis.assistant.AssistantIntegrationTest;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.assistant.TestSecurityConfiguration.token;

/**
 * The limit of questions per user (ADR 0025). The shared context has a high limit, so this test registers a low
 * one for its own users first; blank questions are used, so the provider is never called.
 */
@AssistantIntegrationTest
class QuestionRateLimitApiTest {

	@Autowired
	WebTestClient client;

	@Autowired
	RateLimiterRegistry limiters;

	private WebTestClient.ResponseSpec ask(String user) {
		return client.post().uri("/asistente/consulta").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(user, "DOCTOR"))
				.bodyValue(Map.of("pregunta", " ")).exchange();
	}

	private void limit(String user, int questions) {
		limiters.rateLimiter("question:" + user, RateLimiterConfig.custom().limitForPeriod(questions)
				.limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build());
	}

	@Test
	void aUserOverTheLimitGetsA429WithRetryAfterAndOtherUsersAreNotAffected() {
		limit("doctor-limited", 2);
		limit("doctor-other", 2);

		ask("doctor-limited").expectStatus().isBadRequest();
		ask("doctor-limited").expectStatus().isBadRequest();
		ask("doctor-limited").expectStatus().isEqualTo(429)
				.expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
				.expectBody()
				.jsonPath("$.status").isEqualTo(429)
				.jsonPath("$.detail").isEqualTo("Too many questions: at most 2 every 60 seconds per user")
				.jsonPath("$.traceId").exists();

		ask("doctor-other").expectStatus().isBadRequest();
	}

}
