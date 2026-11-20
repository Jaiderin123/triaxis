package dev.triaxis.triage.infrastructure.adapter.in.scheduler;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.triage.application.port.in.EscalateOverduePatientsUseCase;
import dev.triaxis.triage.infrastructure.config.EscalationProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationSchedulerTest {

	/** Returns the scripted results in order, then zero; an error entry fails that call. */
	private static final class ScriptedEscalation implements EscalateOverduePatientsUseCase {

		private final Deque<Object> script;
		private final AtomicInteger calls = new AtomicInteger();

		ScriptedEscalation(List<Object> script) {
			this.script = new ArrayDeque<>(script);
		}

		@Override
		public Mono<Integer> escalateOverdue() {
			return Mono.defer(() -> {
				calls.incrementAndGet();
				Object next = script.isEmpty() ? 0 : script.poll();
				return next instanceof RuntimeException error ? Mono.error(error) : Mono.just((Integer) next);
			});
		}

		@Override
		public int batchSize() {
			return 2;
		}

	}

	private static EscalationScheduler scheduler(EscalateOverduePatientsUseCase escalation, Duration interval) {
		return new EscalationScheduler(escalation, new EscalationProperties(true, interval, 2));
	}

	@Test
	void aFullBatchStartsAnotherOneAtOnce() {
		ScriptedEscalation escalation = new ScriptedEscalation(List.of(2, 2, 1));

		StepVerifier.create(scheduler(escalation, Duration.ofHours(1)).runCycle()).expectNext(5).verifyComplete();

		assertThat(escalation.calls).hasValue(3);
	}

	@Test
	void aFailedCycleDoesNotStopTheLoop() throws Exception {
		ScriptedEscalation escalation = new ScriptedEscalation(List.of(new IllegalStateException("database down"), 1));
		EscalationScheduler scheduler = scheduler(escalation, Duration.ofMillis(50));

		scheduler.start();
		try {
			for (int attempt = 0; attempt < 100 && escalation.calls.get() < 3; attempt++) {
				Thread.sleep(20);
			}
			assertThat(scheduler.isRunning()).isTrue();
			assertThat(escalation.calls.get()).as("cycles after the failure").isGreaterThanOrEqualTo(3);
		}
		finally {
			scheduler.stop();
		}
		assertThat(scheduler.isRunning()).isFalse();
	}

}
