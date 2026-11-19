package dev.triaxis.triage.infrastructure.adapter.out.persistence.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("patients")
public record PatientEntity(
		@Id UUID id,
		String fullName,
		Integer age,
		Integer heartRate,
		Integer systolic,
		Integer diastolic,
		Integer spo2,
		BigDecimal temperature,
		Integer pain,
		Integer gcs,
		String chiefComplaint,
		String level,
		Integer slaMinutes,
		String status,
		boolean escalated,
		OffsetDateTime escalatedAt,
		OffsetDateTime registeredAt,
		OffsetDateTime statusChangedAt,
		OffsetDateTime consultationStartedAt) {
}
