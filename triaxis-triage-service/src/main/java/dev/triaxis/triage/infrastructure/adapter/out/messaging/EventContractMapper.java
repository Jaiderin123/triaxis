package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

/** Converts domain events to the Spanish message contract of {@code triaje.eventos}, and back for the consumers. */
@Mapper(imports = TriageEvent.class)
public interface EventContractMapper {

	@Mapping(target = "idEvento", source = "eventId")
	@Mapping(target = "tipo", source = "type")
	@Mapping(target = "version", expression = "java(TriageEvent.CURRENT_VERSION)")
	@Mapping(target = "ocurridoEn", source = "occurredAt")
	@Mapping(target = "pacienteId", source = "patientId")
	@Mapping(target = "nivel", source = "level")
	@Mapping(target = "estadoAnterior", source = "previousStatus")
	@Mapping(target = "estadoNuevo", source = "newStatus")
	@Mapping(target = "slaMinutos", source = "slaMinutes")
	@Mapping(target = "escalado", source = "escalated")
	TriageEvent toContract(PatientEvent event);

	@ValueMapping(source = "REGISTERED", target = "PACIENTE_REGISTRADO")
	@ValueMapping(source = "CONSULTATION_STARTED", target = "PACIENTE_EN_CONSULTA")
	@ValueMapping(source = "DISCHARGED", target = "PACIENTE_ALTA")
	@ValueMapping(source = "ESCALATED", target = "PACIENTE_ESCALADO")
	EventType toType(PatientEventType type);

	@ValueMapping(source = "CRITICAL", target = "CRITICO")
	@ValueMapping(source = "URGENT", target = "URGENTE")
	@ValueMapping(source = "URGENT_2", target = "URGENTE_2")
	@ValueMapping(source = "NOT_URGENT", target = "NO_URGENTE")
	@ValueMapping(source = "NOT_URGENT_2", target = "NO_URGENTE_2")
	EventLevel toLevel(TriageLevel level);

	@ValueMapping(source = "REGISTERED", target = "REGISTRADO")
	@ValueMapping(source = "IN_CONSULTATION", target = "EN_CONSULTA")
	@ValueMapping(source = "DISCHARGED", target = "ALTA")
	EventStatus toStatus(PatientStatus status);

	/** Reverse of {@link #toContract}; the version is not part of the domain event. */
	@InheritInverseConfiguration(name = "toContract")
	PatientEvent toDomain(TriageEvent event);

	@InheritInverseConfiguration(name = "toType")
	PatientEventType toDomainType(EventType type);

	@InheritInverseConfiguration(name = "toLevel")
	TriageLevel toDomainLevel(EventLevel level);

	@InheritInverseConfiguration(name = "toStatus")
	PatientStatus toDomainStatus(EventStatus status);

}
