package dev.triaxis.triage.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.application.service.EscalateOverduePatientsService;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.infrastructure.adapter.out.messaging.OutboxRelay;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static dev.triaxis.triage.infrastructure.adapter.in.messaging.AlertTestSupport.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end with the webhook channel: a real escalation goes through the outbox, the relay and Kafka to a fake
 * webhook receiver. BlockHound is installed when the test JVM starts (BlockHoundSessionListener), so a blocking call on a non-blocking thread fails the handling (the
 * listener retries it and the alert never arrives). This class has its own context, with its own containers.
 */
@TriageIntegrationTest
class EscalationAlertWebhookTest {

	private static final String PATH = "/hooks/test-secret";

	/** Bodies received by the fake webhook, by patient id. */
	private static final Map<String, BlockingQueue<JsonNode>> RECEIVED = new ConcurrentHashMap<>();
	/** Patients whose alerts the fake webhook refuses with 500. */
	private static final Set<String> REFUSED = ConcurrentHashMap.newKeySet();
	private static final ObjectMapper JSON = new ObjectMapper();

	private static DisposableServer webhook;

	@DynamicPropertySource
	static void webhookChannel(DynamicPropertyRegistry registry) {
		webhook = HttpServer.create().host("127.0.0.1").port(0)
				.route(routes -> routes.post(PATH, (request, response) -> request.receive().aggregate().asString()
						.map(EscalationAlertWebhookTest::parse)
						.flatMap(body -> {
							String patient = body.get("pacienteId").asText();
							RECEIVED.computeIfAbsent(patient, key -> new LinkedBlockingQueue<>()).add(body);
							HttpResponseStatus status = REFUSED.contains(patient)
									? HttpResponseStatus.INTERNAL_SERVER_ERROR : HttpResponseStatus.OK;
							return response.status(status).send().then();
						})))
				.bindNow();
		registry.add("triage.alerts.channel", () -> "webhook");
		registry.add("triage.alerts.webhook.url", () -> "http://127.0.0.1:" + webhook.port() + PATH);
		registry.add("triage.alerts.backoff", () -> "PT0.05S");
	}

	@AfterAll
	static void stopWebhook() {
		webhook.disposeNow();
	}

	private static JsonNode parse(String body) {
		try {
			return JSON.readTree(body);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

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

	@Autowired
	OutboxRelay relay;

	@Autowired
	DatabaseClient client;

	@Autowired
	MeterRegistry meters;

	/** A level 1 patient (SLA of 5 minutes) waiting since the given second after the epoch. */
	private Patient waiting(Instant epoch, int secondsAfterEpoch) {
		return patients.save(PersistenceTestSupport.registeredAt(epoch.plusSeconds(secondsAfterEpoch),
				PatientStatus.REGISTERED)).block();
	}

	/** Escalates for real at the given time and relays the outbox to Kafka. */
	private void escalateAndRelay(Instant at) {
		new EscalateOverduePatientsService(patients, audit, events, ids, () -> at, unitOfWork, metrics, 100)
				.escalateOverdue().block();
		int sent;
		do {
			sent = relay.relayOnce().block();
		}
		while (sent > 0);
	}

	private String outcomeOf(Patient patient) {
		return client.sql("SELECT outcome FROM alert_deliveries WHERE patient_id = :id")
				.bind("id", patient.id())
				.map(row -> row.get("outcome", String.class))
				.one().block();
	}

	private static JsonNode received(Patient patient) throws InterruptedException {
		return RECEIVED.computeIfAbsent(patient.id().toString(), key -> new LinkedBlockingQueue<>())
				.poll(30, TimeUnit.SECONDS);
	}

	@Test
	void aRealEscalationReachesTheWebhook() throws Exception {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient patient = waiting(epoch, 0);

		escalateAndRelay(epoch.plus(Duration.ofMinutes(12)));

		JsonNode body = received(patient);
		assertThat(body).as("the alert at the webhook").isNotNull();
		assertThat(body.get("text").asText())
				.isEqualTo("Paciente escalado: nivel CRITICO, 12 min de espera (SLA 5 min). Ver /triage/pacientes/"
						+ patient.id());
		assertThat(body.get("tipo").asText()).isEqualTo("paciente_escalado");
		assertThat(body.get("nivel").asText()).isEqualTo("CRITICO");
		assertThat(body.get("slaMinutos").asInt()).isEqualTo(5);
		assertThat(body.get("minutosEsperando").asLong()).isEqualTo(12);
		assertThat(body.get("ruta").asText()).isEqualTo("/triage/pacientes/" + patient.id());
		assertThat(body.toString()).doesNotContain(patient.fullName(), patient.chiefComplaint());
		await("the delivery record", () -> "SENT".equals(outcomeOf(patient)));
	}

	@Test
	void aPatientThatEnteredConsultationBeforeTheAlertIsSkipped() throws Exception {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient patient = waiting(epoch, 0);
		Instant escalatedAt = epoch.plus(Duration.ofMinutes(12));
		new EscalateOverduePatientsService(patients, audit, events, ids, () -> escalatedAt, unitOfWork, metrics, 100)
				.escalateOverdue().block();
		patients.updateStatus(patient.id(), PatientStatus.REGISTERED, PatientStatus.IN_CONSULTATION,
				escalatedAt.plusSeconds(30)).block();
		// A later patient, alerted after the skipped one: once it arrives, the skipped event was handled too.
		Patient later = waiting(epoch, 1);

		escalateAndRelay(escalatedAt.plusSeconds(60));

		assertThat(received(later)).as("the alert of the later patient").isNotNull();
		await("the skipped delivery", () -> "SKIPPED".equals(outcomeOf(patient)));
		assertThat(RECEIVED.get(patient.id().toString())).as("nothing sent for the skipped patient").isNullOrEmpty();
	}

	@Test
	void aRefusingWebhookIsCountedAsFailedTheEscalationStaysAndTheNextAlertIsSent() throws Exception {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient refused = waiting(epoch, 0);
		Patient next = waiting(epoch, 1);
		REFUSED.add(refused.id().toString());
		double failedBefore = meters.counter("triage.alerts", "outcome", "failed").count();

		escalateAndRelay(epoch.plus(Duration.ofMinutes(12)));

		assertThat(received(next)).as("the alert of the next patient").isNotNull();
		await("the failed delivery", () -> "FAILED".equals(outcomeOf(refused)));
		assertThat(RECEIVED.get(refused.id().toString())).as("three attempts").hasSize(3);
		assertThat(meters.counter("triage.alerts", "outcome", "failed").count()).isEqualTo(failedBefore + 1);
		// The escalation does not depend on the channel: the flag and its audit entry were committed.
		assertThat(patients.findById(refused.id()).block().escalated()).isTrue();
		long audited = client.sql("SELECT count(*) AS total FROM patient_audit WHERE patient_id = :id "
						+ "AND event_type = 'ESCALATED'")
				.bind("id", refused.id())
				.map(row -> row.get("total", Long.class))
				.one().block();
		assertThat(audited).isEqualTo(1);
	}

}
