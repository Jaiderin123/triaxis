package dev.triaxis.assistant.domain.exception;

/**
 * The question cannot be answered right now: the language model provider is unavailable (failure, timeout or
 * open circuit breaker) or the corpus is not embedded yet. It becomes a 503 and never a generated answer.
 */
public class AssistantUnavailableException extends RuntimeException {

	public AssistantUnavailableException(String message) {
		super(message);
	}

	public AssistantUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
