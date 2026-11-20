package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static dev.triaxis.triage.TestSecurityConfiguration.token;
import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class OutboxIntegrationTest {

	@Autowired
	WebTestClient client;

	@Autowired
	DatabaseClient database;

	@Autowired
	ObjectMapper json;

	@Autowired
	PatientRepository patients;

	@Autowired
	EventPublisher events;

	@Autowired
	UnitOfWork unitOfWork;

	private static final Map<String, Object> CRITICAL_PATIENT = Map.ofEntries(
			Map.entry("nombreCompleto", "Ana Gomez"), Map.entry("edad", 54), Map.entry("frecuenciaCardiaca", 130),
			Map.entry("presionSistolica", 180), Map.entry("presionDiastolica", 110), Map.entry("spo2", 97),
			Map.entry("temperatura", 36.8), Map.entry("dolor", 8), Map.entry("gcs", 15),
			Map.entry("quejaPrincipal", "Dolor toracico"));

	private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
	private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

	/** Posts a patient; the optional traceparent plays the role of a caller that already has a trace. */
	private WebTestClient.ResponseSpec post(String traceparent) {
		WebTestClient.RequestBodySpec request = client.post().uri("/triage/pacientes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("nurse-ana", "NURSE"))
				.contentType(MediaType.APPLICATION_JSON);
		if (traceparent != null) {
			request = request.header("traceparent", traceparent);
		}
		return request.bodyValue(CRITICAL_PATIENT).exchange();
	}

	private List<Map<String, Object>> outboxRows(String patientId) {
		return database.sql("""
				SELECT event_type, message_key, correlation_id, traceparent, payload::text AS payload, published_at
				FROM outbox_events WHERE message_key = :key ORDER BY id
				""").bind("key", patientId)
				.map(row -> Map.<String, Object>of(
						"type", row.get("event_type", String.class),
						"key", row.get("message_key", String.class),
						"correlation", String.valueOf(row.get("correlation_id", String.class)),
						"traceparent", String.valueOf(row.get("traceparent", String.class)),
						"payload", row.get("payload", String.class),
						"published", row.get("published_at", java.time.OffsetDateTime.class) != null))
				.all().collectList().block();
	}

	@Test
	void registrationWritesTheEventToTheOutboxWithTheTraceOfTheRequest() throws Exception {
		String id = post(TRACEPARENT).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		List<Map<String, Object>> rows = outboxRows(id);

		assertThat(rows).hasSize(1);
		Map<String, Object> row = rows.get(0);
		assertThat(row).containsEntry("type", "paciente_registrado").containsEntry("key", id)
				.containsEntry("correlation", TRACE_ID).containsEntry("published", false);
		assertThat((String) row.get("traceparent")).startsWith("00-" + TRACE_ID + "-");
		TriageEvent event = json.readValue((String) row.get("payload"), TriageEvent.class);
		assertThat(event.tipo()).isEqualTo(EventType.PACIENTE_REGISTRADO);
		assertThat(event.version()).isEqualTo(1);
		assertThat(event.pacienteId()).hasToString(id);
		assertThat(event.nivel()).isEqualTo(EventLevel.CRITICO);
		assertThat(event.estadoAnterior()).isNull();
		assertThat(event.estadoNuevo()).isEqualTo(EventStatus.REGISTRADO);
		assertThat(event.slaMinutos()).isEqualTo(5);
		assertThat(event.escalado()).isFalse();
		assertThat(event.idEvento()).isNotNull();
	}

	@Test
	void eachTransitionAddsAnEventInOrder() {
		String id = post(null).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		for (String status : List.of("EN_CONSULTA", "ALTA")) {
			client.patch().uri("/triage/pacientes/{id}/estado", id)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("doctor-luis", "DOCTOR"))
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(Map.of("estado", status))
					.exchange().expectStatus().isOk();
		}

		assertThat(outboxRows(id)).extracting(row -> row.get("type"))
				.containsExactly("paciente_registrado", "paciente_en_consulta", "paciente_alta");
	}

	@Test
	void rejectedChangesLeaveNoEvent() {
		String id = post(null).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		client.patch().uri("/triage/pacientes/{id}/estado", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("nurse-ana", "NURSE"))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("estado", "REGISTRADO"))
				.exchange().expectStatus().isEqualTo(409);

		assertThat(outboxRows(id)).hasSize(1);
	}

	@Test
	void aRequestWithoutTraceGetsANewTraceThatIsStoredWithTheEvent() {
		String id = post(null).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		Map<String, Object> row = outboxRows(id).get(0);

		assertThat((String) row.get("correlation")).matches("[0-9a-f]{32}");
		assertThat((String) row.get("traceparent")).startsWith("00-" + row.get("correlation") + "-");
	}

	@Test
	void anInvalidTraceparentIsIgnoredAndANewTraceIsStarted() {
		String id = post("not-a-traceparent").expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		assertThat((String) outboxRows(id).get(0).get("correlation")).matches("[0-9a-f]{32}")
				.isNotEqualTo(TRACE_ID);
	}

	@Test
	void theEventIsRolledBackWithTheRestOfTheUnitOfWork() {
		Patient patient = PersistenceTestSupport.newPatient();
		PatientEvent event = PatientEvent.registered(UUID.randomUUID(), patient);

		unitOfWork.execute(patients.save(patient)
				.then(events.publish(event))
				.then(Mono.<Void>error(new IllegalStateException("boom")))).onErrorResume(error -> Mono.empty()).block();

		assertThat(outboxRows(patient.id().toString())).isEmpty();
		assertThat(patients.findById(patient.id()).block()).isNull();
	}

	@Test
	void anEventWrittenInAUnitOfWorkIsKeptOnCommit() {
		Patient patient = PersistenceTestSupport.newPatient();
		PatientEvent event = new PatientEvent(UUID.randomUUID(), PatientEventType.REGISTERED,
				Instant.now().truncatedTo(ChronoUnit.MICROS), patient.id(), TriageLevel.CRITICAL, null,
				PatientStatus.REGISTERED, 5, false);

		unitOfWork.execute(patients.save(patient).then(events.publish(event))).block();

		assertThat(outboxRows(patient.id().toString())).hasSize(1);
	}

}
