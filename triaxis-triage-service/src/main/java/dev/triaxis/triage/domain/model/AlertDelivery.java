package dev.triaxis.triage.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Record of a handled escalation event: the event is never alerted again (ASSUMPTIONS A45). */
public record AlertDelivery(UUID eventId, UUID patientId, AlertOutcome outcome, Instant handledAt) {

	public AlertDelivery {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(patientId, "patientId");
		Objects.requireNonNull(handledAt, "handledAt");
		if (outcome == null || outcome == AlertOutcome.DUPLICATE) {
			throw new IllegalArgumentException("A duplicate is counted, never recorded");
		}
	}

}
