package dev.triaxis.assistant.infrastructure.adapter.in.messaging;

import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

/** Converts the Spanish message contract of {@code triaje.eventos} to the urgency event of the domain. */
@Mapper
public interface TriageEventMapper {

	@Mapping(target = "patientId", source = "pacienteId")
	@Mapping(target = "level", source = "nivel")
	@Mapping(target = "kind", source = "tipo")
	@Mapping(target = "occurredAt", source = "ocurridoEn")
	UrgencyEvent toUrgencyEvent(TriageEvent event);

	@ValueMapping(source = "CRITICO", target = "CRITICAL")
	@ValueMapping(source = "URGENTE", target = "URGENT")
	@ValueMapping(source = "URGENTE_2", target = "URGENT_2")
	@ValueMapping(source = "NO_URGENTE", target = "NOT_URGENT")
	@ValueMapping(source = "NO_URGENTE_2", target = "NOT_URGENT_2")
	TriageLevel toLevel(EventLevel level);

	/** Only the discharge ends a patient; every other event says the patient is present (ADR 0021). */
	@ValueMapping(source = "PACIENTE_ALTA", target = "DISCHARGED")
	@ValueMapping(source = "PACIENTE_REGISTRADO", target = "PRESENT")
	@ValueMapping(source = "PACIENTE_EN_CONSULTA", target = "PRESENT")
	@ValueMapping(source = "PACIENTE_ESCALADO", target = "PRESENT")
	UrgencyEvent.Kind toKind(EventType type);

}
