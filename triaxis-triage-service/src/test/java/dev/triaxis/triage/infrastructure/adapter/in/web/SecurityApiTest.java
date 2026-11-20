package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.util.Map;
import java.util.UUID;

import dev.triaxis.triage.TestSecurityConfiguration;
import dev.triaxis.triage.TriageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.bearer;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.changeStatus;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.doctor;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.get;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.notUrgent;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.nurse;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.register;

@TriageIntegrationTest
class SecurityApiTest {

	@Autowired
	WebTestClient client;

	private String registeredPatient() {
		return register(client, nurse(), notUrgent()).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();
	}

	@Test
	void requestsWithoutTokenAreUnauthorized() {
		client.post().uri("/triage/pacientes").bodyValue(notUrgent()).exchange().expectStatus().isUnauthorized();
		client.get().uri("/triage/pacientes/{id}", UUID.randomUUID()).exchange().expectStatus().isUnauthorized();
		client.patch().uri("/triage/pacientes/{id}/estado", UUID.randomUUID()).exchange().expectStatus().isUnauthorized();
	}

	@Test
	void expiredTokensAreUnauthorized() {
		register(client, bearer(TestSecurityConfiguration.expiredToken("nurse-ana", "NURSE")), notUrgent())
				.expectStatus().isUnauthorized();
	}

	@Test
	void tokensSignedByAnotherKeyAreUnauthorized() {
		register(client, bearer(TestSecurityConfiguration.tokenSignedByAnotherKey("nurse-ana", "NURSE")), notUrgent())
				.expectStatus().isUnauthorized();
	}

	@Test
	void garbageTokensAreUnauthorized() {
		register(client, "Bearer not.a.token", notUrgent()).expectStatus().isUnauthorized();
	}

	@Test
	void doctorsCannotRegisterPatients() {
		register(client, doctor(), notUrgent()).expectStatus().isForbidden();
	}

	@Test
	void tokenWithoutKnownRolesIsForbidden() {
		register(client, bearer(TestSecurityConfiguration.token("someone")), notUrgent()).expectStatus().isForbidden();
	}

	@Test
	void doctorsCanReadAndChangeStatus() {
		String id = registeredPatient();

		get(client, doctor(), id).expectStatus().isOk();
		changeStatus(client, doctor(), id, "EN_CONSULTA").expectStatus().isOk();
	}

	@Test
	void nursesCanReadAndChangeStatus() {
		String id = registeredPatient();

		get(client, nurse(), id).expectStatus().isOk();
		changeStatus(client, nurse(), id, "EN_CONSULTA").expectStatus().isOk();
	}

	@Test
	void healthProbesArePublic() {
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
		client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
	}

	@Test
	void unauthorizedAnswersUseTheStandardProblemFormat() {
		client.post().uri("/triage/pacientes").bodyValue(notUrgent()).exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.instance").isEqualTo("/triage/pacientes")
				.jsonPath("$.traceId").isNotEmpty()
				.jsonPath("$.timestamp").isNotEmpty();
	}

	@Test
	void forbiddenAnswersUseTheStandardProblemFormat() {
		register(client, doctor(), notUrgent()).expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.detail").isEqualTo("Access denied")
				.jsonPath("$.traceId").isNotEmpty();
	}

	@Test
	void aTokenForAnotherAudienceIsRejected() throws Exception {
		String token = TestSecurityConfiguration.tokenForAudience("another-system", "nurse-ana", "NURSE");

		register(client, bearer(token), notUrgent()).expectStatus().isUnauthorized();
	}

	@Test
	void unknownPathsNeedAValidTokenToo() {
		client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
		client.get().uri("/nothing-here").exchange().expectStatus().isUnauthorized();
	}

	/** Deny by default: a route without a rule is refused, so an unknown path does not reveal what exists. */
	@Test
	void anUnknownRouteIsDeniedForAnAuthenticatedUserInTheStandardFormat() {
		client.get().uri("/nothing-here").header(HttpHeaders.AUTHORIZATION, nurse()).exchange()
				.expectStatus().isForbidden()
				.expectBody().jsonPath("$.status").isEqualTo(403).jsonPath("$.instance").isEqualTo("/nothing-here");
	}

	@Test
	void notFoundForAnAuthenticatedUserUsesTheStandardFormat() {
		String path = "/triage/pacientes/" + java.util.UUID.randomUUID();
		client.get().uri(path).header(HttpHeaders.AUTHORIZATION, nurse()).exchange()
				.expectStatus().isNotFound()
				.expectBody().jsonPath("$.status").isEqualTo(404).jsonPath("$.instance").isEqualTo(path);
	}

	@Test
	void metricsNeedTheScraperCredentials() {
		client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized()
				.expectHeader().valueMatches("WWW-Authenticate", "Basic realm=.*");
		client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("scraper", "wrong")).exchange()
				.expectStatus().isUnauthorized();
		// A valid API token is not accepted either: the scraper has its own credentials.
		client.get().uri("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, nurse()).exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void metricsExposeTheBusinessCountersToTheScraper() {
		register(client, nurse(), notUrgent()).expectStatus().isCreated();

		// The metrics text is larger than the 64 KB limit of the services, so the test client reads more.
		client.mutate().codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build()
				.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("scraper", "test-metrics-secret")).exchange()
				.expectStatus().isOk()
				.expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
						.contains("triage_classification_total").contains("triage_outbox_pending")
						.contains("application=\"triaxis-triage-service\""));
	}

}
