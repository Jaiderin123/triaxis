package dev.triaxis.auth.infrastructure.adapter.in.web.validation;

import java.util.List;

public class RequestValidationException extends RuntimeException {

	private final List<String> fields;

	public RequestValidationException(List<String> fields) {
		super("Invalid field(s): " + String.join(", ", fields));
		this.fields = List.copyOf(fields);
	}

	public List<String> fields() {
		return fields;
	}

}
