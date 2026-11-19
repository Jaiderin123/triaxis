package dev.triaxis.triage.domain.exception;

import java.util.UUID;

public class PatientNotFoundException extends RuntimeException {

	public PatientNotFoundException(UUID id) {
		super("Patient " + id + " not found");
	}

}
