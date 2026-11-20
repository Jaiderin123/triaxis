package dev.triaxis.auth.infrastructure.adapter.in.web.ratelimit;

public class TooManyAttemptsException extends RuntimeException {

	private final long retryAfterSeconds;

	public TooManyAttemptsException(long retryAfterSeconds) {
		super("Too many attempts, try again later");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}

}
