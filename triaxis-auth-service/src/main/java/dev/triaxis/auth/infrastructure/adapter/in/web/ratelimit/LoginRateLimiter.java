package dev.triaxis.auth.infrastructure.adapter.in.web.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window limiter for the token endpoint: at most {@code maxAttempts} per key in each window. It slows
 * down password guessing. It lives in memory, so with several replicas each one limits on its own.
 */
public class LoginRateLimiter {

	private static final int CLEAN_UP_THRESHOLD = 10_000;

	private record Window(Instant start, int attempts) {
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final int maxAttempts;
	private final Duration window;
	private final Clock clock;

	public LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
		this.maxAttempts = maxAttempts;
		this.window = window;
		this.clock = clock;
	}

	/** Counts an attempt for the key and fails with {@link TooManyAttemptsException} when the limit is exceeded. */
	public void check(String key) {
		Instant now = clock.instant();
		if (windows.size() > CLEAN_UP_THRESHOLD) {
			windows.values().removeIf(w -> w.start().plus(window).isBefore(now));
		}
		Window current = windows.compute(key, (k, existing) -> {
			if (existing == null || !existing.start().plus(window).isAfter(now)) {
				return new Window(now, 1);
			}
			return new Window(existing.start(), existing.attempts() + 1);
		});
		if (current.attempts() > maxAttempts) {
			long retry = Math.max(1, Duration.between(now, current.start().plus(window)).toSeconds());
			throw new TooManyAttemptsException(retry);
		}
	}

}
