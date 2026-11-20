package dev.triaxis.assistant.domain.exception;

/** A protocol file of the corpus does not follow the expected format. The service refuses to start with it. */
public class InvalidProtocolException extends RuntimeException {

	public InvalidProtocolException(String source, String reason) {
		super("Invalid protocol " + source + ": " + reason);
	}

}
