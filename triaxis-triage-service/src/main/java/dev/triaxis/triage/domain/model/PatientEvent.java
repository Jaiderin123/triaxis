package dev.triaxis.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Something that happened to a patient. It carries the level so consumers need no further lookups. */
public record PatientEvent(
		UUID eventId,
		PatientEventType type,
		Instant occurredAt,
		UUID patientId,
		TriageLevel level,
		PatientStatus previousStatus,
		PatientStatus newStatus,
		int slaMinutes,
		boolean escalated) {

	public static PatientEvent registered(UUID eventId, Patient patient) {
		return new PatientEvent(eventId, PatientEventType.REGISTERED, patient.registeredAt(), patient.id(),
				patient.level(), null, patient.status(), patient.slaMinutes(), patient.escalated());
	}

	/** The event type follows the new status: entering consultation or discharge. */
	public static PatientEvent statusChanged(UUID eventId, Patient updated, PatientStatus previous, Instant at) {
		PatientEventType type = switch (updated.status()) {
			case IN_CONSULTATION -> PatientEventType.CONSULTATION_STARTED;
			case DISCHARGED -> PatientEventType.DISCHARGED;
			case REGISTERED -> throw new IllegalArgumentException("A patient cannot go back to REGISTERED");
		};
		return new PatientEvent(eventId, type, at, updated.id(), updated.level(), previous, updated.status(),
				updated.slaMinutes(), updated.escalated());
	}

}
