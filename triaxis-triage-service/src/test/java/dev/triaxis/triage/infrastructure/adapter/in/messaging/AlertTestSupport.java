package dev.triaxis.triage.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

final class AlertTestSupport {

	private AlertTestSupport() {
	}

	/** Polls an asynchronous result (the listener runs on its own thread); it never orders two threads. */
	static void await(String what, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (!condition.getAsBoolean()) {
			assertThat(System.nanoTime() < deadline).as("timed out waiting for " + what).isTrue();
			Thread.sleep(50);
		}
	}

}
