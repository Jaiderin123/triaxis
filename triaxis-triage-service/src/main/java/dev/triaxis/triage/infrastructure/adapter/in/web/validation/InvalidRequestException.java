package dev.triaxis.triage.infrastructure.adapter.in.web.validation;

import java.util.List;

import dev.triaxis.triage.domain.exception.FieldViolation;

/** A request problem found by the web layer; field names are already the external ones. */
public class InvalidRequestException extends RuntimeException {

	private final List<FieldViolation> violations;

	public InvalidRequestException(String field, String message) {
		super(field + " " + message);
		this.violations = List.of(new FieldViolation(field, message));
	}

	public List<FieldViolation> violations() {
		return violations;
	}

}
