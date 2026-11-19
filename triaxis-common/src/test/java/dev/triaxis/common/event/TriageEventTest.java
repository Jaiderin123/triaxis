package dev.triaxis.common.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TriageEventTest {

	private static final UUID EVENT_ID = UUID.fromString("6f9c0d0e-0000-4000-8000-000000000001");
	private static final UUID PATIENT_ID = UUID.fromString("3a1b0000-0000-4000-8000-000000000002");

	private final ObjectMapper mapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	private static TriageEvent registered() {
		return new TriageEvent(EVENT_ID, EventType.PACIENTE_REGISTRADO, TriageEvent.CURRENT_VERSION,
				Instant.parse("2026-11-18T15:30:00Z"), PATIENT_ID, EventLevel.CRITICO, null,
				EventStatus.REGISTRADO, 5, false);
	}

	@Test
	void serializesWithTheSpanishFieldNamesOfTheContract() throws Exception {
		JsonNode json = mapper.readTree(mapper.writeValueAsString(registered()));

		assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("idEvento", "tipo", "version",
				"ocurridoEn", "pacienteId", "nivel", "estadoAnterior", "estadoNuevo", "slaMinutos", "escalado");
		assertThat(json.get("idEvento").asText()).isEqualTo(EVENT_ID.toString());
		assertThat(json.get("tipo").asText()).isEqualTo("paciente_registrado");
		assertThat(json.get("version").asInt()).isEqualTo(1);
		assertThat(json.get("ocurridoEn").asText()).isEqualTo("2026-11-18T15:30:00Z");
		assertThat(json.get("pacienteId").asText()).isEqualTo(PATIENT_ID.toString());
		assertThat(json.get("nivel").asText()).isEqualTo("CRITICO");
		assertThat(json.get("estadoNuevo").asText()).isEqualTo("REGISTRADO");
		assertThat(json.get("slaMinutos").asInt()).isEqualTo(5);
		assertThat(json.get("escalado").asBoolean()).isFalse();
	}

	@Test
	void theRegistrationHasANullPreviousStatusThatIsStillPresent() throws Exception {
		JsonNode json = mapper.readTree(mapper.writeValueAsString(registered()));

		assertThat(json.has("estadoAnterior")).isTrue();
		assertThat(json.get("estadoAnterior").isNull()).isTrue();
	}

	@Test
	void survivesARoundTrip() throws Exception {
		TriageEvent event = new TriageEvent(EVENT_ID, EventType.PACIENTE_ALTA, 1, Instant.parse("2026-11-18T16:00:00Z"),
				PATIENT_ID, EventLevel.NO_URGENTE_2, EventStatus.EN_CONSULTA, EventStatus.ALTA, 120, true);

		TriageEvent copy = mapper.readValue(mapper.writeValueAsString(event), TriageEvent.class);

		assertThat(copy).isEqualTo(event);
	}

	@ParameterizedTest
	@CsvSource({
			"PACIENTE_REGISTRADO, paciente_registrado",
			"PACIENTE_EN_CONSULTA, paciente_en_consulta",
			"PACIENTE_ALTA, paciente_alta",
			"PACIENTE_ESCALADO, paciente_escalado"
	})
	void eventNamesMatchTheSpecification(EventType type, String expected) throws Exception {
		assertThat(type.value()).isEqualTo(expected);
		assertThat(mapper.writeValueAsString(type)).isEqualTo("\"" + expected + "\"");
		assertThat(mapper.readValue("\"" + expected + "\"", EventType.class)).isEqualTo(type);
	}

	@Test
	void levelAndStatusValuesMatchTheContract() {
		assertThat(EventLevel.values()).extracting(Enum::name)
				.containsExactly("CRITICO", "URGENTE", "URGENTE_2", "NO_URGENTE", "NO_URGENTE_2");
		assertThat(EventStatus.values()).extracting(Enum::name)
				.containsExactly("REGISTRADO", "EN_CONSULTA", "ALTA");
	}

	@Test
	void exposesTheTopicAndTheHeaderNames() {
		assertThat(TriageEvent.TOPIC).isEqualTo("triaje.eventos");
		assertThat(TriageEvent.CORRELATION_ID_HEADER).isEqualTo("correlationId");
		assertThat(TriageEvent.EVENT_TYPE_HEADER).isEqualTo("eventType");
	}

}
