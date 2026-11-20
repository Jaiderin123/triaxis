package dev.triaxis.triage.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.application.port.in.NotifyEscalationUseCase;
import dev.triaxis.triage.domain.model.AlertOutcome;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.infrastructure.adapter.out.messaging.EventContractMapper;
import dev.triaxis.triage.infrastructure.config.AlertProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Alerts the medical team of escalated patients (RF-06, ADR 0011). It reads {@code triaje.eventos} in its own
 * consumer group, independent from the escalation transaction and from the assistant.
 *
 * <ul>
 * <li>Asynchronous: it returns a {@link Mono} and acknowledges a record only after it was handled, so a patient
 * escalated while the listener is down is alerted when it comes back. At most {@value #MAX_IN_FLIGHT} events are
 * handled at a time; the consumer thread waits for a free slot (it is not an event loop).</li>
 * <li>Other event types are acknowledged at once. A record that is not a valid event is logged without its payload,
 * counted in {@code triage.alerts.invalid} and skipped, so it never blocks the partition.</li>
 * <li>A failing channel is an outcome of the use case, not an error. An error means the event was not handled (the
 * database does not answer): it is retried with back-off until it works. Each try is bounded by a timeout derived
 * from the alert settings.</li>
 * <li>The alert content and the payload are never logged here; only the event id and the outcome are.</li>
 * </ul>
 */
@Component
class EscalationAlertListener {

	static final String GROUP = "triage-escalation-alerts";
	static final String CONTAINER = "escalation-alerts";
	static final int MAX_IN_FLIGHT = 16;

	private static final Logger log = LoggerFactory.getLogger(EscalationAlertListener.class);

	/** Time left for the two database statements of one event, on top of the worst case of the channel. */
	private static final Duration STORE_MARGIN = Duration.ofSeconds(30);

	private final NotifyEscalationUseCase alerts;
	private final EventContractMapper mapper;
	private final ObjectMapper json;
	private final MeterRegistry meters;
	private final Duration handlingTimeout;
	private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);

	EscalationAlertListener(NotifyEscalationUseCase alerts, EventContractMapper mapper, ObjectMapper json,
			MeterRegistry meters, AlertProperties properties) {
		this.alerts = alerts;
		this.mapper = mapper;
		this.json = json;
		this.meters = meters;
		this.handlingTimeout = handlingTimeout(properties);
	}

	/** Every try of the channel at its longest, the waits between them, and the margin of the database. */
	static Duration handlingTimeout(AlertProperties properties) {
		Duration call = properties.webhook().connectTimeout().plus(properties.webhook().responseTimeout());
		Duration waits = properties.backoff().multipliedBy((1L << (properties.attempts() - 1)) - 1);
		return call.multipliedBy(properties.attempts()).plus(waits).plus(STORE_MARGIN);
	}

	@KafkaListener(id = CONTAINER, topics = TriageEvent.TOPIC, groupId = GROUP,
			properties = "auto.offset.reset=earliest")
	Mono<Void> onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws InterruptedException {
		TriageEvent event = read(record);
		if (event == null) {
			meters.counter("triage.alerts.invalid").increment();
			ack.acknowledge();
			return Mono.empty();
		}
		if (event.tipo() != EventType.PACIENTE_ESCALADO) {
			ack.acknowledge();
			return Mono.empty();
		}
		PatientEvent escalation = mapper.toDomain(event);
		inFlight.acquire();
		return Mono.defer(() -> alerts.handle(escalation).timeout(handlingTimeout))
				.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(500)).maxBackoff(Duration.ofSeconds(30))
						.doBeforeRetry(signal -> log.warn("Alert of event {} not handled, retrying: {}",
								escalation.eventId(), signal.failure().getClass().getSimpleName())))
				.doOnNext(outcome -> {
					if (outcome == AlertOutcome.FAILED) {
						log.warn("Alert of event {} failed on every attempt and is given up", escalation.eventId());
					}
					ack.acknowledge();
				})
				.doFinally(signal -> inFlight.release())
				.then();
	}

	private TriageEvent read(ConsumerRecord<String, String> record) {
		try {
			TriageEvent event = json.readValue(record.value(), TriageEvent.class);
			if (event.idEvento() == null || event.tipo() == null || event.pacienteId() == null
					|| event.nivel() == null || event.ocurridoEn() == null) {
				throw new IllegalArgumentException("a required field is missing");
			}
			return event;
		}
		catch (Exception invalid) {
			// The payload is not logged: it is not trusted and may hold patient data.
			log.warn("Skipping a record of {} that is not a valid event (partition {}, offset {}): {}",
					record.topic(), record.partition(), record.offset(), invalid.getClass().getSimpleName());
			return null;
		}
	}

}
