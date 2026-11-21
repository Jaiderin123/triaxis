package dev.triaxis.assistant.infrastructure.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A zero or negative wait would make every question a 503 or retry in a tight loop: it stops the startup. */
class DurationSettingsTest {

	@Test
	void theTimeoutsOfTheModelMustBePositive() {
		assertThatNoException().isThrownBy(() -> new LlmProperties.Timeout(Duration.ofSeconds(5), Duration.ofSeconds(20),
				Duration.ofSeconds(30)));
		assertThatThrownBy(() -> new LlmProperties.Timeout(Duration.ofSeconds(5), Duration.ZERO, Duration.ofSeconds(30)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("answer");
		assertThatThrownBy(() -> new LlmProperties.Timeout(Duration.ofSeconds(-1), Duration.ofSeconds(20),
				Duration.ofSeconds(30))).hasMessageContaining("embedding");
	}

	@Test
	void theBackOffOfTheCorpusMustBePositiveAndOrdered() {
		assertThatThrownBy(() -> new CorpusProperties.Retry(Duration.ZERO, Duration.ofMinutes(1)))
				.hasMessageContaining("initial-backoff");
		assertThatThrownBy(() -> new CorpusProperties.Retry(Duration.ofMinutes(2), Duration.ofMinutes(1)))
				.hasMessageContaining("max-backoff");
	}

}
