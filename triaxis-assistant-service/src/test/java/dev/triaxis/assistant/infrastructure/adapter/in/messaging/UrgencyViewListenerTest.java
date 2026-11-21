package dev.triaxis.assistant.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.assistant.AssistantIntegrationTest;
import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyView;
import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The urgency view built from real Kafka messages into a real Redis. Other tests share the topic and the view,
 * so the assertions compare the view before and after the events of each test.
 */
@AssistantIntegrationTest
class UrgencyViewListenerTest {

	@Autowired
	KafkaTemplate<String, String> kafka;

	@Autowired
	ObjectMapper json;

	@Autowired
	UrgencyViewStore store;

	@Autowired
	KafkaListenerEndpointRegistry containers;

	@Autowired
	ReactiveStringRedisTemplate redis;

	@Autowired
	MeterRegistry meters;

	private void send(UUID patient, EventType type, EventLevel level) throws Exception {
		TriageEvent event = new TriageEvent(UUID.randomUUID(), type, 1, Instant.now().truncatedTo(ChronoUnit.MILLIS),
				patient, level, null, EventStatus.REGISTRADO, 5, false);
		kafka.send(TriageEvent.TOPIC, patient.toString(), json.writeValueAsString(event)).get();
	}

	private UrgencyView view() {
		return store.read().block();
	}

	private static Map<TriageLevel, Long> plus(UrgencyView view, TriageLevel level, long delta) {
		Map<TriageLevel, Long> expected = new EnumMap<>(view.active());
		expected.merge(level, delta, Long::sum);
		return expected;
	}

	private static void await(String what, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (!condition.getAsBoolean()) {
			assertThat(System.nanoTime() < deadline).as("timed out waiting for " + what).isTrue();
			Thread.sleep(100);
		}
	}

	private double events(String outcome) {
		return meters.counter("assistant.urgency.events", "outcome", outcome).count();
	}

	@Test
	void registrationsCountAndDischargesSubtractWithDuplicatesIgnored() throws Exception {
		UrgencyView before = view();
		UUID ana = UUID.randomUUID();
		UUID luis = UUID.randomUUID();

		send(ana, EventType.PACIENTE_REGISTRADO, EventLevel.CRITICO);
		send(ana, EventType.PACIENTE_REGISTRADO, EventLevel.CRITICO);
		send(luis, EventType.PACIENTE_REGISTRADO, EventLevel.CRITICO);
		send(luis, EventType.PACIENTE_EN_CONSULTA, EventLevel.CRITICO);
		send(luis, EventType.PACIENTE_ESCALADO, EventLevel.CRITICO);
		send(luis, EventType.PACIENTE_ALTA, EventLevel.CRITICO);
		send(luis, EventType.PACIENTE_ALTA, EventLevel.CRITICO);

		Map<TriageLevel, Long> expected = plus(before, TriageLevel.CRITICAL, 1);
		await("one more critical patient", () -> view().active().equals(expected));
		Thread.sleep(500);
		assertThat(view().active()).isEqualTo(expected);
	}

	@Test
	void aPatientSeenOnlyInConsultationIsCountedAtItsLevel() throws Exception {
		UrgencyView before = view();

		send(UUID.randomUUID(), EventType.PACIENTE_EN_CONSULTA, EventLevel.NO_URGENTE_2);

		await("the patient in consultation", () -> view().active().equals(plus(before, TriageLevel.NOT_URGENT_2, 1)));
	}

	@Test
	void anInvalidRecordIsSkippedAndTheNextOnesAreStillApplied() throws Exception {
		UrgencyView before = view();
		double invalidBefore = events("invalid");

		kafka.send(TriageEvent.TOPIC, "not-a-patient", "{ this is not an event").get();
		kafka.send(TriageEvent.TOPIC, "partial", "{\"tipo\": \"paciente_alta\"}").get();
		send(UUID.randomUUID(), EventType.PACIENTE_REGISTRADO, EventLevel.URGENTE);

		await("the valid event after the invalid ones",
				() -> view().active().equals(plus(before, TriageLevel.URGENT, 1)));
		await("both invalid records counted", () -> events("invalid") >= invalidBefore + 2);
	}

	/** Redis loses the view and the service restarts: the consumer replays the topic and rebuilds the same counts. */
	@Test
	void aLostViewIsRebuiltByReplayingTheTopicAfterARestart() throws Exception {
		send(UUID.randomUUID(), EventType.PACIENTE_REGISTRADO, EventLevel.URGENTE_2);
		restartWithAnEmptyRedis();
		await("the replay", () -> view().total() > 0);
		UrgencyView replayed = settled();

		restartWithAnEmptyRedis();
		await("the same view after the second replay", () -> view().active().equals(replayed.active()));
		assertThat(view().active().get(TriageLevel.URGENT_2)).isPositive();
	}

	/**
	 * Redis is lost while the listener runs and an event arrives before the restart (it recreates the metadata):
	 * the restart still rebuilds the whole view, because only a reset marks the view as built.
	 */
	@Test
	void aViewLostWhileRunningIsRebuiltAtTheNextRestart() throws Exception {
		send(UUID.randomUUID(), EventType.PACIENTE_REGISTRADO, EventLevel.NO_URGENTE);
		// Start from a view rebuilt from the topic only: other tests of the shared context write to Redis directly,
		// and those writes are not in the topic, so they would never come back in the rebuilt view.
		restartWithAnEmptyRedis();
		await("the replay", () -> view().active().get(TriageLevel.NOT_URGENT) > 0);
		UrgencyView before = settled();
		assertThat(before.active().get(TriageLevel.NOT_URGENT)).isPositive();

		flushRedis();
		send(UUID.randomUUID(), EventType.PACIENTE_REGISTRADO, EventLevel.URGENTE);
		await("the event after the loss", () -> view().active().get(TriageLevel.URGENT) > 0);
		assertThat(view().total()).isEqualTo(1);

		restart();

		Map<TriageLevel, Long> expected = plus(before, TriageLevel.URGENT, 1);
		await("the whole view rebuilt", () -> view().active().equals(expected));
	}

	private void restart() throws InterruptedException {
		MessageListenerContainer container = containers.getListenerContainer(UrgencyViewListener.CONTAINER);
		container.stop();
		await("the listener to stop", () -> !container.isRunning());
		container.start();
	}

	private void flushRedis() {
		java.util.List<String> keys = redis.scan().collectList().block();
		if (!keys.isEmpty()) {
			redis.delete(keys.toArray(String[]::new)).block();
		}
	}

	private void restartWithAnEmptyRedis() throws InterruptedException {
		MessageListenerContainer container = containers.getListenerContainer(UrgencyViewListener.CONTAINER);
		container.stop();
		await("the listener to stop", () -> !container.isRunning());
		flushRedis();
		assertThat(view().total()).isZero();
		container.start();
	}

	/** Waits until no event was applied for a second, and returns the view. */
	private UrgencyView settled() throws InterruptedException {
		double seen;
		do {
			seen = events("applied") + events("unchanged");
			Thread.sleep(1000);
		}
		while (events("applied") + events("unchanged") != seen);
		return view();
	}

}
