package dev.triaxis.assistant.infrastructure.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.triaxis.assistant.AssistantIntegrationTest;
import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static dev.triaxis.assistant.TestSecurityConfiguration.token;
import static org.assertj.core.api.Assertions.assertThat;

@AssistantIntegrationTest
class EstadoUrgenciasApiTest {

	@Autowired
	WebTestClient client;

	@Autowired
	UrgencyViewStore store;

	private WebTestClient.ResponseSpec get(String bearer) {
		WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/asistente/estado-urgencias");
		if (bearer != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
		}
		return request.exchange();
	}

	@Test
	void showsTheFiveLevelsInPriorityOrderTheTotalAndTheTimeOfTheNewestEvent() {
		store.apply(new UrgencyEvent(UUID.randomUUID(), TriageLevel.CRITICAL, UrgencyEvent.Kind.PRESENT,
				java.time.Instant.parse("2099-01-01T00:00:00Z"))).block();

		@SuppressWarnings("unchecked")
		Map<String, Object> body = get(token("nurse-ana", "NURSE")).expectStatus().isOk()
				.expectBody(Map.class).returnResult().getResponseBody();

		@SuppressWarnings("unchecked")
		Map<String, Number> levels = (Map<String, Number>) body.get("porNivel");
		assertThat(new ArrayList<>(levels.keySet()))
				.containsExactly("CRITICO", "URGENTE", "URGENTE_2", "NO_URGENTE", "NO_URGENTE_2");
		long sum = levels.values().stream().mapToLong(Number::longValue).sum();
		assertThat(((Number) body.get("total")).longValue()).isEqualTo(sum);
		assertThat(levels.get("CRITICO").longValue()).isPositive();
		assertThat(body.get("actualizadoEn")).isEqualTo("2099-01-01T00:00:00Z");
	}

	@Test
	void nursesAndDoctorsCanReadItAndAnonymousCallersCannot() {
		get(token("doctor-luis", "DOCTOR")).expectStatus().isOk();
		get(token("nurse-ana", "NURSE")).expectStatus().isOk();
		get(null).expectStatus().isUnauthorized();
		assertThat(List.of()).isEmpty();
	}

}
