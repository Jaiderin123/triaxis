package dev.triaxis.assistant.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class TriageEventMapperTest {

	private final TriageEventMapper mapper = Mappers.getMapper(TriageEventMapper.class);

	@ParameterizedTest
	@CsvSource({"PACIENTE_REGISTRADO, PRESENT", "PACIENTE_EN_CONSULTA, PRESENT", "PACIENTE_ESCALADO, PRESENT",
			"PACIENTE_ALTA, DISCHARGED"})
	void onlyTheDischargeEndsAPatient(EventType type, UrgencyEvent.Kind kind) {
		UUID patient = UUID.randomUUID();
		Instant at = Instant.parse("2026-11-19T12:00:00Z");
		TriageEvent event = new TriageEvent(UUID.randomUUID(), type, 1, at, patient, EventLevel.URGENTE_2, null,
				EventStatus.REGISTRADO, 30, false);

		assertThat(mapper.toUrgencyEvent(event)).isEqualTo(new UrgencyEvent(patient, TriageLevel.URGENT_2, kind, at));
	}

}
