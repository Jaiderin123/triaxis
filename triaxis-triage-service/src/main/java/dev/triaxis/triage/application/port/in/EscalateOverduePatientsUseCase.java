package dev.triaxis.triage.application.port.in;

import reactor.core.publisher.Mono;

/** Escalates the patients that exceeded their waiting-time SLA (RF-03, RN-03). */
public interface EscalateOverduePatientsUseCase {

	/**
	 * Escalates one batch of overdue patients, oldest first, and returns how many were escalated. A result
	 * equal to {@link #batchSize()} means more patients may be waiting.
	 */
	Mono<Integer> escalateOverdue();

	int batchSize();

}
