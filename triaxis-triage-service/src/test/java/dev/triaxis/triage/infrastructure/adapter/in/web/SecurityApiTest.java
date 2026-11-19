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
	void otherPathsNeedAValidTokenToo() {
		client.get().uri("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "").exchange()
				.expectStatus().isUnauthorized();
	}

}
