package dev.triaxis.triage.application.port.out;

import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import reactor.core.publisher.Mono;

public interface PatientRepository {

	Mono<Patient> save(Patient patient);

	Mono<Patient> findById(UUID id);

	/**
	 * Conditional update: changes the status only while the patient is still in {@code expected}.
	 * Completes empty when no row matched (the patient does not exist or its status changed).
	 */
	Mono<Patient> updateStatus(UUID id, PatientStatus expected, PatientStatus next, Instant now);

}
