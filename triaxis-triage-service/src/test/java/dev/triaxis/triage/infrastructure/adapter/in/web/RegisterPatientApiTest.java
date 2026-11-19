package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.util.Map;
import java.util.UUID;

import dev.triaxis.triage.TriageIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.nurse;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.notUrgent;
import static dev.triaxis.triage.infrastructure.adapter.in.web.ApiTestSupport.register;
import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class RegisterPatientApiTest {

	@Autowired
	WebTestClient client;

	@Autowired
	DatabaseClient database;

	@Autowired
	MeterRegistry meters;

	@Test
	void registersAndClassifiesAPatient() {
		Map<String, Object> body = notUrgent();
		body.put("frecuenciaCardiaca", 130);
		body.put("presionSistolica", 180);
		body.put("presionDiastolica", 110);
		body.put("dolor", 8);
		body.put("quejaPrincipal", "Dolor torácico");

		register(client, nurse(), body).expectStatus().isCreated()
				.expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location).startsWith("/triage/pacientes/"))
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.id").isNotEmpty()
				.jsonPath("$.nivel").isEqualTo("CRITICO")
				.jsonPath("$.slaMinutos").isEqualTo(5)
				.jsonPath("$.estado").isEqualTo("REGISTRADO")
				.jsonPath("$.fechaRegistro").value(value -> assertThat((String) value).endsWith("Z"));
	}

	@ParameterizedTest(name = "{0} is {9}")
	@CsvSource({
			"E1, 130, 180, 110, 97, 36.8, 8, 15, Dolor torácico, CRITICO",
			"E2, 95, 150, 95, 91, 37.0, 4, 15, Dificultad para respirar, URGENTE",
			"E3, 88, 130, 85, 98, 38.2, 6, 15, Fiebre, URGENTE_2",
			"E4, 72, 120, 80, 99, 36.5, 2, 15, Herida por cortar, NO_URGENTE",
			"E5, 70, 115, 75, 99, 36.4, 0, 15, Renovación de recetario, NO_URGENTE_2",
			"E6, 35, 80, 50, 85, 35.9, 0, 6, Inconsciente, CRITICO"
	})
	void classifiesTheReferencePatients(String id, int hr, int sys, int dia, int spo2, double temp, int pain, int gcs,
			String complaint, String expected) {
		Map<String, Object> body = notUrgent();
		body.put("frecuenciaCardiaca", hr);
		body.put("presionSistolica", sys);
		body.put("presionDiastolica", dia);
		body.put("spo2", spo2);
		body.put("temperatura", temp);
		body.put("dolor", pain);
		body.put("gcs", gcs);
		body.put("quejaPrincipal", complaint);

		register(client, nurse(), body).expectStatus().isCreated()
				.expectBody().jsonPath("$.nivel").isEqualTo(expected);
	}

	@Test
	void acceptsTheAliasesOfTheFieldNames() {
		Map<String, Object> body = Map.ofEntries(
				Map.entry("nombre", "Luis Perez"), Map.entry("age", 40), Map.entry("fc", 72),
				Map.entry("sistolica", 120), Map.entry("diastolica", 80), Map.entry("SpO2", 99),
				Map.entry("temp", 36.5), Map.entry("pain", 1), Map.entry("GCS", 15),
				Map.entry("queja", "Consulta general"));

		register(client, nurse(), body).expectStatus().isCreated()
				.expectBody().jsonPath("$.nivel").isEqualTo("NO_URGENTE");
	}

	@Test
	void auditsTheRegistrationWithTheActorAndCountsTheClassification() {
		double before = meters.counter("triage.classification", "level", "NOT_URGENT").count();

		String id = register(client, nurse(), notUrgent()).expectStatus().isCreated()
				.expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

		Map<String, Object> audit = database.sql("""
				SELECT actor, event_type, previous_status, new_status FROM patient_audit WHERE patient_id = :id
				""").bind("id", UUID.fromString(id))
				.map(row -> Map.<String, Object>of("actor", row.get("actor", String.class),
						"event", row.get("event_type", String.class), "new", row.get("new_status", String.class)))
				.one().block();
		assertThat(audit).containsEntry("actor", "nurse-ana").containsEntry("event", "REGISTERED")
				.containsEntry("new", "REGISTERED");
		assertThat(meters.counter("triage.classification", "level", "NOT_URGENT").count()).isEqualTo(before + 1);
	}

	@Test
	void everyInvalidFieldIsNamedWithItsExternalName() {
		Map<String, Object> body = notUrgent();
		body.put("frecuenciaCardiaca", 300);
		body.put("spo2", 50);
		body.put("nombreCompleto", "Al");

		register(client, nurse(), body).expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.detail").value(detail -> assertThat((String) detail)
						.contains("frecuenciaCardiaca").contains("spo2").contains("nombreCompleto"))
				.jsonPath("$.errores.length()").isEqualTo(3);
	}

	@Test
	void diastolicMustBeLowerThanSystolic() {
		Map<String, Object> body = notUrgent();
		body.put("presionSistolica", 100);
		body.put("presionDiastolica", 100);

		register(client, nurse(), body).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("presionDiastolica");
	}

	@Test
	void missingFieldsAreReported() {
		register(client, nurse(), Map.of("nombreCompleto", "Ana Gomez")).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail)
						.contains("edad").contains("frecuenciaCardiaca").contains("quejaPrincipal"));
	}

	@Test
	void decimalsAreRejectedForIntegerFields() {
		Map<String, Object> body = notUrgent();
		body.put("spo2", 93.5);

		register(client, nurse(), body).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("spo2");
	}

	@Test
	void textInANumericFieldIsRejectedAndNamed() {
		Map<String, Object> body = notUrgent();
		body.put("edad", "treinta");

		register(client, nurse(), body).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("edad");
	}

	@Test
	void numbersAsStringsAreNotAccepted() {
		Map<String, Object> body = notUrgent();
		body.put("frecuenciaCardiaca", "72");

		register(client, nurse(), body).expectStatus().isBadRequest();
	}

	@Test
	void malformedJsonIsABadRequest() {
		client.post().uri("/triage/pacientes")
				.header(HttpHeaders.AUTHORIZATION, nurse())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{not json")
				.exchange().expectStatus().isBadRequest();
	}

	@Test
	void emptyBodyIsABadRequest() {
		client.post().uri("/triage/pacientes")
				.header(HttpHeaders.AUTHORIZATION, nurse())
				.contentType(MediaType.APPLICATION_JSON)
				.exchange().expectStatus().isBadRequest();
	}

	@Test
	void nonJsonBodyIsUnsupported() {
		client.post().uri("/triage/pacientes")
				.header(HttpHeaders.AUTHORIZATION, nurse())
				.contentType(MediaType.TEXT_PLAIN)
				.bodyValue("hello")
				.exchange().expectStatus().isEqualTo(415);
	}

}
