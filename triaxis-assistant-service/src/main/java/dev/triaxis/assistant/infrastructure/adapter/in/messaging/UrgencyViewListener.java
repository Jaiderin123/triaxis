package dev.triaxis.assistant.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.assistant.application.port.in.ApplyTriageEventUseCase;
import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.common.event.TriageEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Builds the urgency view from {@code triaje.eventos} (RF-04 B), in its own consumer group. The listener is
 * asynchronous (ADR 0001): it returns a {@link Mono} and acknowledges the record only after Redis applied it, so
 * the offset is committed only for applied events. Records may be applied out of order, which the rules of ADR
 * 0021 allow. A failure of Redis is retried until it works; a record that is not a valid event is logged,
 * counted and skipped, so it never blocks the partition.
 *
 * <p>When the store was never built (a new or lost Redis), the consumer reads the topic again from the beginning
 * when its partitions are assigned (at startup or after a restart), before it processes any record, and the same
 * rules rebuild the counts. A seek while records are in flight is avoided on purpose: with asynchronous
 * acknowledgments it does not replay the records.
 */
@Component
class UrgencyViewListener extends AbstractConsumerSeekAware {

	static final String GROUP = "assistant-urgency-view";
	static final String CONTAINER = "urgency-view";

	private static final Logger log = LoggerFactory.getLogger(UrgencyViewListener.class);
	private static final Duration STARTUP_CHECK = Duration.ofSeconds(30);

	private final ApplyTriageEventUseCase urgency;
	private final UrgencyViewStore store;
	private final TriageEventMapper mapper;
	private final ObjectMapper json;
	private final MeterRegistry meters;
	private final Semaphore inFlight;
	private final ObjectProvider<KafkaListenerEndpointRegistry> containers;
	private final int startupAttempts;
	private final Duration startupBackoff;
	private final Duration restartDelay;

	UrgencyViewListener(ApplyTriageEventUseCase urgency, UrgencyViewStore store, TriageEventMapper mapper,
			ObjectMapper json, MeterRegistry meters, ObjectProvider<KafkaListenerEndpointRegistry> containers,
			@Value("${assistant.urgency.max-in-flight:64}") int maxInFlight,
			@Value("${assistant.urgency.startup-check.attempts:5}") int startupAttempts,
			@Value("${assistant.urgency.startup-check.backoff:PT2S}") Duration startupBackoff,
			@Value("${assistant.urgency.startup-check.restart-delay:PT30S}") Duration restartDelay) {
		if (startupAttempts < 1) {
			throw new IllegalArgumentException("assistant.urgency.startup-check.attempts must be at least 1");
		}
		this.containers = containers;
		this.startupAttempts = startupAttempts;
		this.startupBackoff = startupBackoff;
		this.restartDelay = restartDelay;
		this.urgency = urgency;
		this.store = store;
		this.mapper = mapper;
		this.json = json;
		this.meters = meters;
		this.inFlight = new Semaphore(maxInFlight);
	}

	@KafkaListener(id = UrgencyViewListener.CONTAINER, topics = TriageEvent.TOPIC, groupId = GROUP)
	Mono<Void> onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws InterruptedException {
		UrgencyEvent event = read(record);
		if (event == null) {
			count("invalid");
			ack.acknowledge();
			return Mono.empty();
		}
		// Runs on the consumer thread, not on an event loop: waiting here slows down the polling when Redis is slow.
		inFlight.acquire();
		return urgency.apply(event)
				.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(500)).maxBackoff(Duration.ofSeconds(30))
						.doBeforeRetry(signal -> log.warn("Urgency view update failed, retrying: {}",
								signal.failure().toString())))
				.doOnNext(changed -> {
					count(changed ? "applied" : "unchanged");
					ack.acknowledge();
				})
				.doFinally(signal -> inFlight.release())
				.then();
	}

	private UrgencyEvent read(ConsumerRecord<String, String> record) {
		try {
			TriageEvent event = json.readValue(record.value(), TriageEvent.class);
			if (event.pacienteId() == null || event.nivel() == null || event.tipo() == null
					|| event.ocurridoEn() == null) {
				throw new IllegalArgumentException("a required field is missing");
			}
			return mapper.toUrgencyEvent(event);
		}
		catch (Exception invalid) {
			// The payload is not logged: it is not trusted and may be large.
			log.warn("Skipping a record of {} that is not a valid event (partition {}, offset {}): {}",
					record.topic(), record.partition(), record.offset(), invalid.getClass().getSimpleName());
			return null;
		}
	}

	private void count(String outcome) {
		meters.counter("assistant.urgency.events", "outcome", outcome).increment();
	}

	/**
	 * Runs on the consumer thread when partitions are assigned (blocking is allowed there). When Redis does not
	 * answer, the check is retried a few times; if it still fails, the listener container is restarted later, so
	 * the check (and the rebuild, if needed) runs again when the partitions are assigned again. The attempts must
	 * stay well below {@code max.poll.interval.ms} (5 minutes by default).
	 */
	@Override
	public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
		super.onPartitionsAssigned(assignments, callback);
		for (int attempt = 1; attempt <= startupAttempts; attempt++) {
			try {
				if (!Boolean.TRUE.equals(store.isInitialized().block(STARTUP_CHECK))) {
					log.info("The urgency view is not built: rebuilding it from the beginning of {}", TriageEvent.TOPIC);
					store.reset().block(STARTUP_CHECK);
					callback.seekToBeginning(assignments.keySet());
				}
				return;
			}
			catch (RuntimeException unavailable) {
				log.warn("Could not check the urgency view (attempt {} of {}): {}", attempt, startupAttempts,
						unavailable.toString());
				if (attempt < startupAttempts) {
					pause(startupBackoff);
				}
			}
		}
		meters.counter("assistant.urgency.startup.failures").increment();
		log.error("Redis does not answer: the urgency view is checked again after a restart of the listener in {}",
				restartDelay);
		Schedulers.boundedElastic().schedule(this::restartContainer, restartDelay.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void restartContainer() {
		KafkaListenerEndpointRegistry registry = containers.getIfAvailable();
		MessageListenerContainer container = registry == null ? null : registry.getListenerContainer(CONTAINER);
		if (container != null) {
			container.stop();
			container.start();
		}
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

}
