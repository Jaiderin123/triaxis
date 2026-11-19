package dev.triaxis.triage.infrastructure.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.triaxis.triage.TestSecurityConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

final class ApiTestSupport {

	private ApiTestSupport() {
	}

	static String bearer(String token) {
		return "Bearer " + token;
	}

	static String nurse() {
		return bearer(TestSecurityConfiguration.token("nurse-ana", "NURSE"));
	}

	static String doctor() {
		return bearer(TestSecurityConfiguration.token("doctor-luis", "DOCTOR"));
	}

	/** A valid registration of a patient that is not urgent (E4 of the requirements). */
	static Map<String, Object> notUrgent() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("nombreCompleto", "Ana Gomez");
		body.put("edad", 34);
		body.put("frecuenciaCardiaca", 72);
		body.put("presionSistolica", 120);
		body.put("presionDiastolica", 80);
		body.put("spo2", 99);
		body.put("temperatura", 36.5);
		body.put("dolor", 2);
		body.put("gcs", 15);
		body.put("quejaPrincipal", "Herida por cortar");
		return body;
	}

	static WebTestClient.ResponseSpec register(WebTestClient client, String authorization, Object body) {
		return client.post().uri("/triage/pacientes")
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.exchange();
	}

	static WebTestClient.ResponseSpec changeStatus(WebTestClient client, String authorization, Object id,
			String status) {
		return client.patch().uri("/triage/pacientes/{id}/estado", id)
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("estado", status))
				.exchange();
	}

	static WebTestClient.ResponseSpec get(WebTestClient client, String authorization, Object id) {
		return client.get().uri("/triage/pacientes/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.exchange();
	}

}
