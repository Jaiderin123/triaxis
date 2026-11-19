package dev.triaxis.triage.domain.exception;

import java.util.List;

public class InvalidPatientDataException extends RuntimeException {

	private final List<FieldViolation> violations;

	public InvalidPatientDataException(List<FieldViolation> violations) {
		super("Invalid patient data: " + violations.stream()
				.map(v -> v.field() + " " + v.message())
				.reduce((a, b) -> a + "; " + b).orElse(""));
		this.violations = List.copyOf(violations);
	}

	public List<FieldViolation> violations() {
		return violations;
	}

}
