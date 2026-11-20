package dev.triaxis.triage.domain.exception;

import java.util.List;

/** The range of the operation report is missing, empty, inverted or too long (ASSUMPTIONS A42). */
public class InvalidReportRangeException extends RuntimeException {

	private final List<FieldViolation> violations;

	public InvalidReportRangeException(List<FieldViolation> violations) {
		super("Invalid report range: " + violations.stream()
				.map(v -> v.field() + " " + v.message())
				.reduce((a, b) -> a + "; " + b).orElse(""));
		this.violations = List.copyOf(violations);
	}

	public List<FieldViolation> violations() {
		return violations;
	}

}
