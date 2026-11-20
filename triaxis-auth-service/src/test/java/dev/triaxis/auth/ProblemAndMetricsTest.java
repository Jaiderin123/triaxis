package dev.triaxis.auth;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = {"triaxis.web.metrics.username=scraper", "triaxis.web.metrics.password=auth-scrape-secret"})
@AutoConfigureWebTestClient
@AutoConfigureObservability
@ActiveProfiles("local")
class ProblemAndMetricsTest {

	@Autowired
	WebTestClient client;

	@Test
	void everyErrorUsesTheStandardFormatWithTheTrace() {
		client.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("username", "nurse", "password", "wrong")).exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.instance").isEqualTo("/auth/token")
				.jsonPath("$.traceId").isNotEmpty()
				.jsonPath("$.timestamp").isNotEmpty();
	}

	@Test
	void validationErrorsListTheFieldsWithTheirMessages() {
		client.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("username", "nurse", "password", "")).exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.errores[0].campo").isEqualTo("password")
				.jsonPath("$.errores[0].mensaje").isNotEmpty();
	}

	@Test
	void deniedPathsUseTheStandardFormat() {
		client.get().uri("/anything").exchange()
				.expectStatus().isUnauthorized()
				.expectBody().jsonPath("$.status").isEqualTo(401).jsonPath("$.instance").isEqualTo("/anything");
	}

	@Test
	void metricsNeedTheScraperCredentials() {
		client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
		client.mutate().codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build()
				.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("scraper", "auth-scrape-secret"))
				.exchange().expectStatus().isOk()
				.expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
						.contains("application=\"triaxis-auth-service\""));
	}

}
