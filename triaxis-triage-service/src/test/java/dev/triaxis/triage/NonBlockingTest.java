package dev.triaxis.triage;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves BlockHound is active (installed when the test JVM starts by {@link BlockHoundSessionListener}), so every
 * test can rely on reactive flows never blocking.
 */
class NonBlockingTest {

	@Test
	void blockingCallOnNonBlockingThreadIsDetected() {
		Mono<String> blocking = Mono.delay(Duration.ofMillis(1))
				.map(tick -> {
					try {
						Thread.sleep(10);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return "done";
				});

		assertThatThrownBy(blocking::block).hasMessageContaining("Blocking call");
	}

}
