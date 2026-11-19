package dev.triaxis.triage.domain.model;

import java.util.Set;

public enum PatientStatus {
	REGISTERED,
	IN_CONSULTATION,
	DISCHARGED;

	/** States this one may move to. */
	public Set<PatientStatus> allowedTransitions() {
		return switch (this) {
			case REGISTERED -> Set.of(IN_CONSULTATION, DISCHARGED);
			case IN_CONSULTATION -> Set.of(DISCHARGED);
			case DISCHARGED -> Set.of();
		};
	}

	public boolean canMoveTo(PatientStatus next) {
		return allowedTransitions().contains(next);
	}
}
