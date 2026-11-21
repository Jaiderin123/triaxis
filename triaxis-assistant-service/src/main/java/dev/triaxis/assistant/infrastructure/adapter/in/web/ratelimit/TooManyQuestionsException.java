package dev.triaxis.assistant.infrastructure.adapter.in.web.ratelimit;

import java.time.Duration;

/** The user asked more questions than the limit of the period; it becomes a 429 with {@code Retry-After}. */
public class TooManyQuestionsException extends RuntimeException {

	private final transient Duration retryAfter;

	public TooManyQuestionsException(int limit, Duration period) {
		super("Too many questions: at most " + limit + " every " + period.toSeconds() + " seconds per user");
		this.retryAfter = period;
	}

	public Duration retryAfter() {
		return retryAfter;
	}

}
