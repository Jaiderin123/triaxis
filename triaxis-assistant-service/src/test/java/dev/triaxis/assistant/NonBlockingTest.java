package dev.triaxis.assistant;

import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves BlockHound is active, so later tests can assert that reactive flows never block.
 */
class NonBlockingTest {

	@BeforeAll
	static void installBlockHound() {
		BlockHound.install();
	}

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
