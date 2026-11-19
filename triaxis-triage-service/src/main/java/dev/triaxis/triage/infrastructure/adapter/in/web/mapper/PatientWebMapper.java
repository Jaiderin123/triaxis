package dev.triaxis.triage.infrastructure.adapter.in.web.mapper;

import java.time.Duration;
import java.time.Instant;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.PatientResponse;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.RegisterPatientRequest;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.RegistrationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Copies data between the Spanish JSON contract and the domain; it takes no business decisions. */
@Mapper
public interface PatientWebMapper {

	@Mapping(target = "fullName", source = "nombreCompleto")
	@Mapping(target = "age", source = "edad")
	@Mapping(target = "heartRate", source = "frecuenciaCardiaca")
	@Mapping(target = "systolic", source = "presionSistolica")
	@Mapping(target = "diastolic", source = "presionDiastolica")
	@Mapping(target = "spo2", source = "spo2")
	@Mapping(target = "temperature", source = "temperatura")
	@Mapping(target = "pain", source = "dolor")
	@Mapping(target = "gcs", source = "gcs")
	@Mapping(target = "chiefComplaint", source = "quejaPrincipal")
	RegistrationData toRegistrationData(RegisterPatientRequest request);

	@Mapping(target = "nivel", source = "level", qualifiedByName = "level")
	@Mapping(target = "slaMinutos", source = "slaMinutes")
	@Mapping(target = "estado", source = "status", qualifiedByName = "status")
	@Mapping(target = "fechaRegistro", source = "registeredAt")
	RegistrationResponse toRegistrationResponse(Patient patient);

	@Mapping(target = "id", source = "patient.id")
	@Mapping(target = "nombreCompleto", source = "patient.fullName")
	@Mapping(target = "edad", source = "patient.age")
	@Mapping(target = "frecuenciaCardiaca", source = "patient.vitalSigns.heartRate")
	@Mapping(target = "presionSistolica", source = "patient.vitalSigns.systolic")
	@Mapping(target = "presionDiastolica", source = "patient.vitalSigns.diastolic")
	@Mapping(target = "spo2", source = "patient.vitalSigns.spo2")
	@Mapping(target = "temperatura", source = "patient.vitalSigns.temperature")
	@Mapping(target = "dolor", source = "patient.vitalSigns.pain")
	@Mapping(target = "gcs", source = "patient.vitalSigns.gcs")
	@Mapping(target = "quejaPrincipal", source = "patient.chiefComplaint")
	@Mapping(target = "nivel", source = "patient.level", qualifiedByName = "level")
	@Mapping(target = "slaMinutos", source = "patient.slaMinutes")
	@Mapping(target = "estado", source = "patient.status", qualifiedByName = "status")
	@Mapping(target = "escalado", source = "patient.escalated")
	@Mapping(target = "fechaRegistro", source = "patient.registeredAt")
	@Mapping(target = "fechaCambioEstado", source = "patient.statusChangedAt")
	@Mapping(target = "tiempoEnEstadoSegundos",
			expression = "java(secondsBetween(patient.statusChangedAt(), now))")
	PatientResponse toResponse(Patient patient, Instant now);

	@Named("level")
	default String level(dev.triaxis.triage.domain.model.TriageLevel level) {
		return ExternalValues.level(level);
	}

	@Named("status")
	default String status(dev.triaxis.triage.domain.model.PatientStatus status) {
		return ExternalValues.status(status);
	}

	default long secondsBetween(Instant from, Instant to) {
		return Math.max(0, Duration.between(from, to).toSeconds());
	}

}
