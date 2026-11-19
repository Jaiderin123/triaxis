package dev.triaxis.triage.infrastructure.adapter.out.persistence.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.VitalSigns;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.entity.PatientEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Copies data between the domain patient and its table row; enums travel by name, instants as UTC. */
@Mapper
public interface PatientEntityMapper {

	@Mapping(target = "heartRate", source = "vitalSigns.heartRate")
	@Mapping(target = "systolic", source = "vitalSigns.systolic")
	@Mapping(target = "diastolic", source = "vitalSigns.diastolic")
	@Mapping(target = "spo2", source = "vitalSigns.spo2")
	@Mapping(target = "temperature", source = "vitalSigns.temperature")
	@Mapping(target = "pain", source = "vitalSigns.pain")
	@Mapping(target = "gcs", source = "vitalSigns.gcs")
	PatientEntity toEntity(Patient patient);

	@Mapping(target = "vitalSigns", source = "entity")
	Patient toDomain(PatientEntity entity);

	default VitalSigns toVitalSigns(PatientEntity entity) {
		return new VitalSigns(entity.heartRate(), entity.systolic(), entity.diastolic(), entity.spo2(),
				entity.temperature(), entity.pain(), entity.gcs());
	}

	default OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	default Instant toInstant(OffsetDateTime time) {
		return time == null ? null : time.toInstant();
	}

}
