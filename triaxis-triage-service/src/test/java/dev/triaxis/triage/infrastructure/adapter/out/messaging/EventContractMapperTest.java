package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractMapperTest {

	private final EventContractMapper mapper = Mappers.getMapper(EventContractMapper.class);

	@Test
	void mapsAnEventToTheSpanishContract() {
		UUID eventId = UUID.randomUUID();
		UUID patientId = UUID.randomUUID();
		Instant at = Instant.parse("2026-11-18T15:30:00Z");
		PatientEvent event = new PatientEvent(eventId, PatientEventType.CONSULTATION_STARTED, at, patientId,
				TriageLevel.URGENT_2, PatientStatus.REGISTERED, PatientStatus.IN_CONSULTATION, 30, true);

		TriageEvent contract = mapper.toContract(event);

		assertThat(contract).isEqualTo(new TriageEvent(eventId, EventType.PACIENTE_EN_CONSULTA,
				TriageEvent.CURRENT_VERSION, at, patientId, EventLevel.URGENTE_2, EventStatus.REGISTRADO,
				EventStatus.EN_CONSULTA, 30, true));
	}

	@Test
	void everyDomainValueHasADistinctContractValue() {
		assertThat(Arrays.stream(PatientEventType.values()).map(mapper::toType)).doesNotContainNull().doesNotHaveDuplicates()
				.hasSize(PatientEventType.values().length);
		assertThat(Arrays.stream(TriageLevel.values()).map(mapper::toLevel)).doesNotContainNull().doesNotHaveDuplicates()
				.hasSize(TriageLevel.values().length);
		assertThat(Arrays.stream(PatientStatus.values()).map(mapper::toStatus)).doesNotContainNull().doesNotHaveDuplicates()
				.hasSize(PatientStatus.values().length);
	}

	@Test
	void theRegistrationKeepsANullPreviousStatus() {
		PatientEvent event = new PatientEvent(UUID.randomUUID(), PatientEventType.REGISTERED, Instant.now(),
				UUID.randomUUID(), TriageLevel.CRITICAL, null, PatientStatus.REGISTERED, 5, false);

		assertThat(mapper.toContract(event).estadoAnterior()).isNull();
	}

}
