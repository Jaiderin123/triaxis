package dev.triaxis.triage.application.port.out;

import java.util.UUID;

import dev.triaxis.triage.domain.model.AlertDelivery;
import reactor.core.publisher.Mono;

/** Handled escalation events, keyed by the event id, so a redelivered event is not alerted twice. */
public interface AlertDeliveryStore {

	Mono<Boolean> isHandled(UUID eventId);

	/** Records the outcome; completes with false when the event was already recorded (the first record wins). */
	Mono<Boolean> record(AlertDelivery delivery);

}
