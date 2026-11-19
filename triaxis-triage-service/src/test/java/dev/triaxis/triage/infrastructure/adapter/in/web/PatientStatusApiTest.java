package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.triaxis.triage.TriageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.changeStatus;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.doctor;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.get;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.notUrgent;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.nurse;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.register;
import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class PatientStatusApiTest {

	@Autowired
	WebTestClient client;

	@Autowired
	DatabaseClient database;

	private String registerPatient() {
		return register(client, nurse(), notUrgent()).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();
	}

	@Test
	void getReturnsTheFullPatient() {
		String id = registerPatient();

		get(client, nurse(), id).expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.id").isEqualTo(id)
				.jsonPath("$.nombreCompleto").isEqualTo("Ana Gomez")
				.jsonPath("$.edad").isEqualTo(34)
				.jsonPath("$.frecuenciaCardiaca").isEqualTo(72)
				.jsonPath("$.presionSistolica").isEqualTo(120)
				.jsonPath("$.presionDiastolica").isEqualTo(80)
				.jsonPath("$.spo2").isEqualTo(99)
				.jsonPath("$.temperatura").isEqualTo(36.5)
				.jsonPath("$.dolor").isEqualTo(2)
				.jsonPath("$.gcs").isEqualTo(15)
				.jsonPath("$.quejaPrincipal").isEqualTo("Herida por cortar")
				.jsonPath("$.nivel").isEqualTo("NO_URGENTE")
				.jsonPath("$.slaMinutos").isEqualTo(60)
				.jsonPath("$.estado").isEqualTo("REGISTRADO")
				.jsonPath("$.escalado").isEqualTo(false)
				.jsonPath("$.fechaRegistro").isNotEmpty()
				.jsonPath("$.fechaCambioEstado").isNotEmpty()
				.jsonPath("$.tiempoEnEstadoSegundos").value(seconds -> assertThat(((Number) seconds).longValue())
						.isBetween(0L, 60L));
	}

	@Test
	void unknownPatientIsNotFound() {
		get(client, nurse(), UUID.randomUUID()).expectStatus().isNotFound()
				.expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("not found"));
		changeStatus(client, nurse(), UUID.randomUUID(), "ALTA").expectStatus().isNotFound();
	}

	@Test
	void malformedIdIsABadRequest() {
		get(client, nurse(), "not-a-uuid").expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("id");
	}

	@Test
	void walksThroughTheWholeFlowAndAuditsEveryStep() {
		String id = registerPatient();

		changeStatus(client, nurse(), id, "EN_CONSULTA").expectStatus().isOk()
				.expectBody().jsonPath("$.estado").isEqualTo("EN_CONSULTA").jsonPath("$.id").isEqualTo(id);
		changeStatus(client, doctor(), id, "ALTA").expectStatus().isOk()
				.expectBody().jsonPath("$.estado").isEqualTo("ALTA");
		get(client, nurse(), id).expectBody().jsonPath("$.estado").isEqualTo("ALTA");

		List<String> trail = database.sql("""
				SELECT actor || ':' || event_type || ':' || coalesce(previous_status, '-') || '>' || new_status AS step
				FROM patient_audit WHERE patient_id = :id ORDER BY id
				""").bind("id", UUID.fromString(id))
				.map(row -> row.get("step", String.class)).all().collectList().block();
		assertThat(trail).containsExactly(
				"nurse-ana:REGISTERED:->REGISTERED",
				"nurse-ana:CONSULTATION_STARTED:REGISTERED>IN_CONSULTATION",
				"doctor-luis:DISCHARGED:IN_CONSULTATION>DISCHARGED");
	}

	@Test
	void dischargesDirectlyFromRegistered() {
		String id = registerPatient();

		changeStatus(client, nurse(), id, "ALTA").expectStatus().isOk()
				.expectBody().jsonPath("$.estado").isEqualTo("ALTA");
	}

	@Test
	void invalidTransitionIsAConflictWithADescriptiveMessage() {
		String id = registerPatient();
		changeStatus(client, nurse(), id, "ALTA").expectStatus().isOk();

		changeStatus(client, nurse(), id, "EN_CONSULTA").expectStatus().isEqualTo(409)
				.expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail)
						.contains("from ALTA to EN_CONSULTA").contains("Allowed from ALTA: none"));
		get(client, nurse(), id).expectBody().jsonPath("$.estado").isEqualTo("ALTA");
	}

	@Test
	void repeatingTheCurrentStatusIsAConflict() {
		String id = registerPatient();

		changeStatus(client, nurse(), id, "REGISTRADO").expectStatus().isEqualTo(409);
	}

	@Test
	void unknownStatusValueIsABadRequestNamingTheField() {
		String id = registerPatient();

		changeStatus(client, nurse(), id, "VOLANDO").expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("estado");
	}

	@Test
	void missingStatusIsABadRequest() {
		String id = registerPatient();

		client.patch().uri("/triage/pacientes/{id}/estado", id)
				.header("Authorization", nurse())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of())
				.exchange().expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("estado");
	}

}
