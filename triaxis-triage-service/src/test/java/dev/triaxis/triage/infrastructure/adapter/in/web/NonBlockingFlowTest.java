package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.application.service.EscalateOverduePatientsService;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.blockhound.BlockHound;
import reactor.core.scheduler.Schedulers;

import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.changeStatus;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.get;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.notUrgent;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.nurse;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.register;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * With BlockHound installed, any blocking call on a non-blocking thread fails the request. The flow
 * covers registration, status changes, reads and the escalation, so it exercises the web, security,
 * persistence, transaction and outbox code.
 */
@TriageIntegrationTest
class NonBlockingFlowTest {

	@Autowired
	WebTestClient client;

	@Autowired
	PatientRepository patients;

	@Autowired
	AuditRepository audit;

	@Autowired
	EventPublisher events;

	@Autowired
	IdGenerator ids;

	@Autowired
	UnitOfWork unitOfWork;

	@Autowired
	TriageMetrics metrics;

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

	@Test
	void theEscalationDoesNotBlockANonBlockingThread() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		patients.save(PersistenceTestSupport.registeredAt(epoch, PatientStatus.REGISTERED)).block();
		EscalateOverduePatientsService escalation = new EscalateOverduePatientsService(patients, audit, events, ids,
				() -> epoch.plus(Duration.ofDays(1)), unitOfWork, metrics, 100);

		Integer escalated = escalation.escalateOverdue().subscribeOn(Schedulers.parallel()).block();

		assertThat(escalated).isEqualTo(1);
	}

}
