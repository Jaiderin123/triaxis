package dev.triaxis.triage.infrastructure.adapter.out.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("patient_audit")
public record AuditEntity(
		@Id Long id,
		UUID patientId,
		String actor,
		String eventType,
		String previousStatus,
		String newStatus,
		boolean escalated,
		OffsetDateTime occurredAt) {
}
