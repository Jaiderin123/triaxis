package dev.triaxis.assistant.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Provider choice and call limits. The circuit breaker itself is configured under
 * {@code resilience4j.circuitbreaker.instances.llm}.
 *
 * @param provider  {@code mock} or {@code gemini}
 * @param timeout   longest wait for each kind of call
 */
@ConfigurationProperties("assistant.llm")
public record LlmProperties(@DefaultValue("mock") String provider, @DefaultValue Timeout timeout) {

	/**
	 * @param embedding   embedding of one text
	 * @param answer      generation of one answer
	 * @param retryAfter  value of the {@code Retry-After} header of a 503
	 */
	public record Timeout(@DefaultValue("PT5S") Duration embedding, @DefaultValue("PT20S") Duration answer,
			@DefaultValue("PT30S") Duration retryAfter) {

		public Timeout {
			requirePositive("assistant.llm.timeout.embedding", embedding);
			requirePositive("assistant.llm.timeout.answer", answer);
			requirePositive("assistant.llm.timeout.retry-after", retryAfter);
		}

	}

	static void requirePositive(String name, Duration value) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(name + " must be a positive duration");
		}
	}

}
