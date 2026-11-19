package dev.triaxis.triage.domain.model;

/** Business events that other services care about. The Spanish names belong to the messaging contract. */
public enum PatientEventType {
	REGISTERED,
	CONSULTATION_STARTED,
	DISCHARGED,
	ESCALATED
}
