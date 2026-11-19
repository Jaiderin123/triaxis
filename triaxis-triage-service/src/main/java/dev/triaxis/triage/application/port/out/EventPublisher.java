package dev.triaxis.triage.application.port.out;

import dev.triaxis.triage.domain.model.PatientEvent;
import reactor.core.publisher.Mono;

/**
 * Registers an event for delivery to other services. It must take part in the current unit of work, so the
 * event exists if and only if the change that caused it was committed.
 */
public interface EventPublisher {

	Mono<Void> publish(PatientEvent event);

}
