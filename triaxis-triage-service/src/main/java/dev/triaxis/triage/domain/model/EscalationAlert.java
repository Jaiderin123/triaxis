package dev.triaxis.triage.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Alert to the medical team about an escalated patient (RF-06). It carries the minimum on purpose: no name,
 * vital signs or complaint, because a channel may be a third party (ASSUMPTIONS A44). The rest is read from the
 * patient with authentication.
 *
 * @param minutesWaiting whole minutes from the registration to the escalation, rounded down
 */
public record EscalationAlert(
		UUID eventId,
		UUID patientId,
		TriageLevel level,
		int slaMinutes,
		long minutesWaiting,
		Instant escalatedAt) {

	/**
	 * Builds the alert of an escalation event. The event has no registration time, so the caller reads it from
	 * the patient.
	 *
	 * @throws IllegalArgumentException when the event is not an escalation or precedes the registration
	 */
	public static EscalationAlert of(PatientEvent event, Instant registeredAt) {
		if (event.type() != PatientEventType.ESCALATED) {
			throw new IllegalArgumentException("Event " + event.eventId() + " is not an escalation");
		}
		if (event.occurredAt().isBefore(registeredAt)) {
			throw new IllegalArgumentException("Event " + event.eventId() + " precedes the registration");
		}
		return new EscalationAlert(event.eventId(), event.patientId(), event.level(), event.slaMinutes(),
				Duration.between(registeredAt, event.occurredAt()).toMinutes(), event.occurredAt());
	}

}
