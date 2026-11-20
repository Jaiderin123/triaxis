package dev.triaxis.assistant.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What the urgency view needs from a triage event (ADR 0021): every event but a discharge says the patient is
 * present, with its level; a discharge ends the patient for the view.
 */
public record UrgencyEvent(UUID patientId, TriageLevel level, Kind kind, Instant occurredAt) {

	public enum Kind {
		/** Registration, consultation or escalation: the patient is active. */
		PRESENT,
		/** Discharge: final, the patient is never counted again. */
		DISCHARGED
	}

	public UrgencyEvent {
		Objects.requireNonNull(patientId, "patientId");
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
