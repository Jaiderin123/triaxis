package dev.triaxis.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

class MetricsEndpointSecurityTest {

	@Nested
	@SpringBootTest(classes = WebTestApp.class, properties = {
			"management.endpoints.web.exposure.include=health,prometheus",
			"triaxis.web.metrics.username=scraper",
			"triaxis.web.metrics.password=scrape-secret"
	})
	@AutoConfigureWebTestClient
	@AutoConfigureObservability
	class WithCredentials {

		@Autowired
		WebTestClient client;

		@Test
		void noCredentialsIsUnauthorizedWithABasicChallenge() {
			client.get().uri("/actuator/prometheus").exchange()
					.expectStatus().isUnauthorized()
					.expectHeader().valueMatches("WWW-Authenticate", "Basic realm=.*")
					.expectBody().jsonPath("$.status").isEqualTo(401);
		}

		@Test
		void wrongPasswordIsUnauthorized() {
			client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("scraper", "nope")).exchange()
					.expectStatus().isUnauthorized();
		}

		@Test
		void aUserOfTheApiCannotReadTheMetrics() {
			client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("ana", "secret")).exchange()
					.expectStatus().isUnauthorized();
		}

		@Test
		void correctCredentialsReturnTheMetrics() {
			client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("scraper", "scrape-secret")).exchange()
					.expectStatus().isOk()
					.expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
							.contains("jvm_memory_used_bytes"));
		}

	}

	@Nested
	@SpringBootTest(classes = WebTestApp.class, properties = {
			"management.endpoints.web.exposure.include=health,prometheus"
	})
	@AutoConfigureWebTestClient
	class WithoutConfiguredPassword {

		@Autowired
		WebTestClient client;

		@Test
		void theEndpointIsDeniedToEverybody() {
			client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
			client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("metrics", "")).exchange()
					.expectStatus().isUnauthorized();
		}

	}

}
