package dev.triaxis.triage.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.exception.PatientNotFoundException;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class GetPatientServiceTest {

	private final FakeTriageStore store = new FakeTriageStore();
	private final GetPatientService service = new GetPatientService(store);

	@Test
	void returnsTheStoredPatient() {
		UUID id = UUID.randomUUID();
		Instant at = Instant.parse("2026-11-18T15:00:00Z");
		Patient patient = new Patient(id, "Ana Gomez", 54,
				new VitalSigns(72, 120, 80, 99, new BigDecimal("36.5"), 2, 15), "Herida por cortar",
				TriageLevel.NOT_URGENT, 60, PatientStatus.REGISTERED, false, null, at, at, null);
		store.patients.put(id, patient);

		StepVerifier.create(service.get(id)).expectNext(patient).verifyComplete();
	}

	@Test
	void unknownPatientIsNotFound() {
		StepVerifier.create(service.get(UUID.randomUUID())).expectError(PatientNotFoundException.class).verify();
	}

}
