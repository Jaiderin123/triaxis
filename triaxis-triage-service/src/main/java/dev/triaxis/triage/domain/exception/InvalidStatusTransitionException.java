package dev.triaxis.triage.domain.exception;

import dev.triaxis.triage.domain.model.PatientStatus;

public class InvalidStatusTransitionException extends RuntimeException {

	public InvalidStatusTransitionException(PatientStatus from, PatientStatus to) {
		super("Cannot change the status from " + from + " to " + to + ". Allowed from " + from + ": "
				+ (from.allowedTransitions().isEmpty() ? "none" : from.allowedTransitions()));
	}

}
