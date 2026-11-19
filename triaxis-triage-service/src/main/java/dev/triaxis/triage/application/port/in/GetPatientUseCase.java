package dev.triaxis.triage.application.port.in;

import java.util.UUID;

import dev.triaxis.triage.domain.model.Patient;
import reactor.core.publisher.Mono;

public interface GetPatientUseCase {

	/** Fails with {@code PatientNotFoundException} when the patient does not exist. */
	Mono<Patient> get(UUID patientId);

}
