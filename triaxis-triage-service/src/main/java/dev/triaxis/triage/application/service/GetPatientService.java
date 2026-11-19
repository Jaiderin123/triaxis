package dev.triaxis.triage.application.service;

import java.util.UUID;

import dev.triaxis.triage.application.port.in.GetPatientUseCase;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.domain.exception.PatientNotFoundException;
import dev.triaxis.triage.domain.model.Patient;
import reactor.core.publisher.Mono;

public class GetPatientService implements GetPatientUseCase {

	private final PatientRepository patients;

	public GetPatientService(PatientRepository patients) {
		this.patients = patients;
	}

	@Override
	public Mono<Patient> get(UUID patientId) {
		return patients.findById(patientId).switchIfEmpty(Mono.error(() -> new PatientNotFoundException(patientId)));
	}

}
