package dev.triaxis.triage.domain.exception;

import java.util.UUID;

/** The patient's status changed between reading it and updating it. */
public class ConcurrentStatusChangeException extends RuntimeException {

	public ConcurrentStatusChangeException(UUID id) {
		super("The status of patient " + id + " was changed by another request; retry with the current status");
	}

}
