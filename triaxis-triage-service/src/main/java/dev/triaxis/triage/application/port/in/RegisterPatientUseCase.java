package dev.triaxis.triage.application.port.in;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.RegistrationData;
import reactor.core.publisher.Mono;

public interface RegisterPatientUseCase {

	/**
	 * Validates, classifies and stores the patient, writing the audit entry in the same transaction.
	 * Fails with {@code InvalidPatientDataException} when the data is invalid.
	 */
	Mono<Patient> register(RegistrationData data, String actor);

}
