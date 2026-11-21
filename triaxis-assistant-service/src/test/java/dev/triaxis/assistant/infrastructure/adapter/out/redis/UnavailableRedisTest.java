package dev.triaxis.assistant.infrastructure.adapter.out.redis;

import java.time.Duration;
import java.time.Instant;

import dev.triaxis.assistant.domain.exception.UrgencyViewUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.test.StepVerifier;

/** A Redis that does not answer is an unavailable view (a 503), never an unexpected error (a 500). */
class UnavailableRedisTest {

	private final LettuceConnectionFactory factory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration("127.0.0.1", 1));

	@AfterEach
	void close() {
		factory.destroy();
	}

	@Test
	void readingTheViewFromAnUnreachableRedisIsAnUnavailableView() {
		factory.afterPropertiesSet();
		RedisUrgencyViewStore store = new RedisUrgencyViewStore(new ReactiveStringRedisTemplate(factory),
				Instant::now, new SimpleMeterRegistry(), Duration.ofDays(30), Duration.ofSeconds(2),
				Duration.ofSeconds(30));

		StepVerifier.create(store.read())
				.expectError(UrgencyViewUnavailableException.class)
				.verify(Duration.ofSeconds(10));
	}

}
