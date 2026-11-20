package dev.triaxis.assistant.infrastructure.adapter.in.web.validation;

/** The request does not have the expected JSON shape; it is a 400 naming the external field. */
public class InvalidRequestException extends RuntimeException {

	private final String field;

	public InvalidRequestException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}

}
