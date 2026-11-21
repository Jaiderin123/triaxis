package dev.triaxis.assistant.infrastructure.adapter.in.web.ratelimit;

import java.time.Duration;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Questions allowed per user (the {@code sub} of the token) in each period, from the rate limiter configuration
 * {@code question} (ADR 0025). Every question counts, answered or not, because each one costs at least the
 * embedding of the provider. It never waits: a question over the limit is refused at once. The users are the
 * accounts of auth, so there is one limiter per known user and the registry stays small.
 */
@Component
public class QuestionRateLimiter {

	static final String CONFIG = "question";

	private final RateLimiterRegistry registry;
	private final Duration period;

	QuestionRateLimiter(RateLimiterRegistry registry) {
		this.registry = registry;
		this.period = registry.getConfiguration(CONFIG)
				.orElseThrow(() -> new IllegalStateException("resilience4j.ratelimiter.configs.question is missing"))
				.getLimitRefreshPeriod();
	}

	/** Completes when the user may ask now, or fails with {@link TooManyQuestionsException}. */
	public Mono<Void> check(String user) {
		return Mono.defer(() -> {
			RateLimiter limiter = registry.rateLimiter(CONFIG + ":" + user, CONFIG);
			return limiter.acquirePermission() ? Mono.<Void>empty()
					: Mono.error(new TooManyQuestionsException(limiter.getRateLimiterConfig().getLimitForPeriod(), period));
		});
	}

}
