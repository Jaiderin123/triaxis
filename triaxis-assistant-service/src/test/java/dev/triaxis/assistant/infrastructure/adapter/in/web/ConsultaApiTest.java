package dev.triaxis.assistant.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;

import dev.triaxis.assistant.AssistantIntegrationTest;
import dev.triaxis.assistant.application.service.CorpusState;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.assistant.TestSecurityConfiguration.token;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code POST /asistente/consulta} end to end with the mock provider, the real corpus and pgvector. */
@AssistantIntegrationTest
class ConsultaApiTest {

	private static final String NOTICE = "No existe un protocolo disponible para esta pregunta.";

	@Autowired
	WebTestClient client;

	@Autowired
	CorpusState corpus;

	@Autowired
	MeterRegistry meters;

	@BeforeEach
	void waitForTheCorpus() throws InterruptedException {
		for (int i = 0; i < 400 && !corpus.isReady(); i++) {
			Thread.sleep(50);
		}
	}

	private WebTestClient.ResponseSpec ask(String bearer, Object body) {
		WebTestClient.RequestBodySpec request = client.post().uri("/asistente/consulta")
				.contentType(MediaType.APPLICATION_JSON);
		if (bearer != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
		}
		return request.bodyValue(body).exchange();
	}

	private static String doctor() {
		return token("doctor-luis", "DOCTOR");
	}

	@Test
	void sampleQuestionOneCitesProto001() {
		ask(doctor(), Map.of("pregunta", "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?"))
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.respuesta").value(text -> assertThat((String) text).contains("Reconfirmar la presión"))
				.jsonPath("$.fuentes.length()").isEqualTo(1)
				.jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001")
				.jsonPath("$.fuentes[0].seccion").isEqualTo("Medidas iniciales");
	}

	@Test
	void sampleQuestionTwoCitesProto002() {
		ask(doctor(), Map.of("pregunta", "¿Cuándo se administra el primer antibiótico en sepsis?"))
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.respuesta").value(text -> assertThat((String) text).contains("primera hora"))
				.jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-002")
				.jsonPath("$.fuentes[0].seccion").isEqualTo("Acciones iniciales");
	}

	@Test
	void sampleQuestionThreeGetsOnlyTheNotice() {
		double before = meters.counter("assistant.answers", "outcome", "no_protocol").count();

		ask(doctor(), Map.of("pregunta", "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?"))
				.expectStatus().isOk()
				.expectBody()
				.json("{\"respuesta\": \"" + NOTICE + "\", \"fuentes\": []}", true);
		assertThat(meters.counter("assistant.answers", "outcome", "no_protocol").count()).isEqualTo(before + 1);
	}

	@Test
	void aBlankQuestionIsABadRequestNamingTheField() {
		ask(doctor(), Map.of("pregunta", "   "))
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.errores[0].campo").isEqualTo("pregunta")
				.jsonPath("$.instance").isEqualTo("/asistente/consulta")
				.jsonPath("$.traceId").exists();
	}

	@Test
	void aMissingQuestionIsABadRequest() {
		ask(doctor(), Map.of("otra", "cosa")).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("pregunta");
	}

	@Test
	void aTooLongQuestionIsABadRequest() {
		ask(doctor(), Map.of("pregunta", "a".repeat(1001))).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].mensaje").value(text -> assertThat((String) text).contains("1000"));
	}

	@Test
	void aQuestionThatIsNotTextIsABadRequest() {
		ask(doctor(), Map.of("pregunta", 42)).expectStatus().isBadRequest()
				.expectBody().jsonPath("$.errores[0].campo").isEqualTo("pregunta");
		ask(doctor(), Map.of("pregunta", List.of("a"))).expectStatus().isBadRequest();
	}

	@Test
	void malformedJsonIsABadRequest() {
		ask(doctor(), "{\"pregunta\": ").expectStatus().isBadRequest();
	}

	@Test
	void withoutATokenItIsUnauthorized() {
		ask(null, Map.of("pregunta", "¿Sepsis?")).expectStatus().isUnauthorized()
				.expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void aNurseIsForbidden() {
		ask(token("nurse-ana", "NURSE"), Map.of("pregunta", "¿Sepsis?")).expectStatus().isForbidden();
	}

}
