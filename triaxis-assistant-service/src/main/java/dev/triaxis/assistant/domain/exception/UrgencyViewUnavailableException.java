package dev.triaxis.assistant.domain.exception;

/**
 * The urgency view cannot be read right now (Redis is down or does not answer in time). It becomes a 503 with
 * {@code Retry-After}, an expected failure of a dependency, not an internal error.
 */
public class UrgencyViewUnavailableException extends RuntimeException {

	public UrgencyViewUnavailableException(Throwable cause) {
		super("The urgency view is temporarily unavailable", cause);
	}

}
