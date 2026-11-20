package dev.triaxis.triage.domain.model;

/** What happened to the alert of one escalation event (ASSUMPTIONS A45). */
public enum AlertOutcome {
	/** The channel accepted the alert. */
	SENT,
	/** The channel refused every attempt, or the alert could not be built; it is not sent again. */
	FAILED,
	/**
	 * The patient no longer waits (in consultation or discharged) when the event is handled: the alert would be
	 * late and useless, so nothing is sent (ADR 0023). It covers the replay of old events by a new consumer group.
	 */
	SKIPPED,
	/** The event was already handled (a redelivery); nothing was sent. */
	DUPLICATE
}
