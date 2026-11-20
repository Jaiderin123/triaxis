package dev.triaxis.auth.infrastructure.adapter.in.web.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-19T10:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

	}

	private final MutableClock clock = new MutableClock();
	private final LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(1), clock);

	@Test
	void allowsTheConfiguredNumberOfAttempts() {
		assertThatCode(() -> {
			limiter.check("user:ana");
			limiter.check("user:ana");
			limiter.check("user:ana");
		}).doesNotThrowAnyException();
	}

	@Test
	void rejectsTheNextAttemptAndTellsWhenToRetry() {
		for (int i = 0; i < 3; i++) {
			limiter.check("user:ana");
		}
		clock.advance(Duration.ofSeconds(20));

		assertThatThrownBy(() -> limiter.check("user:ana"))
				.isInstanceOfSatisfying(TooManyAttemptsException.class,
						e -> assertThat(e.retryAfterSeconds()).isBetween(39L, 41L));
	}

	@Test
	void keysAreIndependent() {
		for (int i = 0; i < 3; i++) {
			limiter.check("user:ana");
		}

		assertThatCode(() -> limiter.check("user:luis")).doesNotThrowAnyException();
	}

	@Test
	void theWindowStartsAgainWhenItEnds() {
		for (int i = 0; i < 3; i++) {
			limiter.check("user:ana");
		}
		clock.advance(Duration.ofMinutes(1).plusSeconds(1));

		assertThatCode(() -> limiter.check("user:ana")).doesNotThrowAnyException();
	}

}
