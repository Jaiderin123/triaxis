package dev.triaxis.auth.infrastructure.adapter.in.web.validation;

import java.util.List;

import dev.triaxis.web.problem.FieldProblem;

public class RequestValidationException extends RuntimeException {

	private final List<FieldProblem> fields;

	public RequestValidationException(List<FieldProblem> fields) {
		super("Invalid field(s): " + fields.stream().map(FieldProblem::campo).reduce((a, b) -> a + ", " + b).orElse(""));
		this.fields = List.copyOf(fields);
	}

	public List<FieldProblem> fields() {
		return fields;
	}

}
