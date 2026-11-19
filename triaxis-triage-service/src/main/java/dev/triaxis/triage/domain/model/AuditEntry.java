package dev.triaxis.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable audit record. {@code previousStatus} is null for the registration;
 * {@code actor} is the JWT subject, or {@code system} for the scheduler.
 */
public record AuditEntry(
		UUID patientId,
		String actor,
		AuditEventType eventType,
		PatientStatus previousStatus,
		PatientStatus newStatus,
		boolean escalated,
		Instant occurredAt) {

	public static final String SYSTEM_ACTOR = "system";

}
