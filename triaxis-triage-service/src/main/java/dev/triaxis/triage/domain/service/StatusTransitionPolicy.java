package dev.triaxis.triage.domain.service;

import dev.triaxis.triage.domain.exception.InvalidStatusTransitionException;
import dev.triaxis.triage.domain.model.PatientStatus;

public class StatusTransitionPolicy {

	/** @throws InvalidStatusTransitionException when the change is not allowed (including staying in the same state) */
	public void ensureAllowed(PatientStatus from, PatientStatus to) {
		if (!from.canMoveTo(to)) {
			throw new InvalidStatusTransitionException(from, to);
		}
	}

}
