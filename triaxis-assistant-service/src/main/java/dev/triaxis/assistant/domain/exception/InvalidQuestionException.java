package dev.triaxis.assistant.domain.exception;

/** The question is blank or too long. The web layer reports it as a 400 naming the external field. */
public class InvalidQuestionException extends RuntimeException {

	public InvalidQuestionException(String message) {
		super(message);
	}

}
