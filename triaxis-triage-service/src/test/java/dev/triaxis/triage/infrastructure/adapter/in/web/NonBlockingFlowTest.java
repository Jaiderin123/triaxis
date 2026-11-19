package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.util.Map;

import dev.triaxis.triage.TriageIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.blockhound.BlockHound;

import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.changeStatus;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.get;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.notUrgent;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.nurse;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.register;

/**
 * With BlockHound installed, any blocking call on a non-blocking thread fails the request. The flow
 * covers registration, status changes and reads, so it exercises the web, security, persistence and
 * transaction code.
 */
@TriageIntegrationTest
class NonBlockingFlowTest {

	@Autowired
	WebTestClient client;

	@BeforeAll
	static void installBlockHound() {
		BlockHound.install();
	}

	@Test
	void registerChangeStatusAndReadDoNotBlockTheEventLoop() {
		String id = register(client, nurse(), notUrgent()).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		changeStatus(client, nurse(), id, "EN_CONSULTA").expectStatus().isOk();
		changeStatus(client, nurse(), id, "ALTA").expectStatus().isOk();
		get(client, nurse(), id).expectStatus().isOk();
	}

}
