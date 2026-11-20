package dev.triaxis.triage.application.port.out;

import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PatientRepository {

	Mono<Patient> save(Patient patient);

	Mono<Patient> findById(UUID id);

	/**
	 * Conditional update: changes the status only while the patient is still in {@code expected}.
	 * Completes empty when no row matched (the patient does not exist or its status changed).
	 */
	Mono<Patient> updateStatus(UUID id, PatientStatus expected, PatientStatus next, Instant now);

	/**
	 * Atomically flags as escalated, at {@code now}, the oldest {@code limit} patients that are still
	 * {@code REGISTERED}, were never escalated and exceeded their SLA strictly before {@code now}, and returns
	 * them. Rows locked by a concurrent change are skipped, so a patient is escalated at most once and never
	 * after it entered consultation or was discharged.
	 */
	Flux<Patient> escalateOverdue(Instant now, int limit);

}
