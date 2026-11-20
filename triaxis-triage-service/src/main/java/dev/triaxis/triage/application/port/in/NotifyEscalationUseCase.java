package dev.triaxis.triage.application.port.in;

import dev.triaxis.triage.domain.model.AlertOutcome;
import dev.triaxis.triage.domain.model.PatientEvent;
import reactor.core.publisher.Mono;

/** Alerts the medical team of an escalated patient, at most once per event (RF-06, ADR 0011). */
public interface NotifyEscalationUseCase {

	/**
	 * Completes with the outcome once it is recorded. A failure of the channel is an outcome ({@code FAILED}),
	 * not an error; an error means the event was not handled (for example, the database is down) and may be
	 * handled again.
	 *
	 * @throws IllegalArgumentException (as an error signal) when the event is not an escalation
	 */
	Mono<AlertOutcome> handle(PatientEvent event);

}
