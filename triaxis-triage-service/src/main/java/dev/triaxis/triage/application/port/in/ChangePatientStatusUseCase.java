package dev.triaxis.triage.application.port.in;

import java.util.UUID;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import reactor.core.publisher.Mono;

public interface ChangePatientStatusUseCase {

	/**
	 * Moves the patient to the target status and audits the change.
	 * Fails with {@code PatientNotFoundException}, {@code InvalidStatusTransitionException} or
	 * {@code ConcurrentStatusChangeException}.
	 */
	Mono<Patient> changeStatus(UUID patientId, PatientStatus target, String actor);

}
