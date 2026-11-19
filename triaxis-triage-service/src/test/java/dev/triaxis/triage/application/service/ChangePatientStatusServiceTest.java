package dev.triaxis.triage.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.exception.ConcurrentStatusChangeException;
import dev.triaxis.triage.domain.exception.InvalidStatusTransitionException;
import dev.triaxis.triage.domain.exception.PatientNotFoundException;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;
import dev.triaxis.triage.domain.service.StatusTransitionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePatientStatusServiceTest {

	private final FakeTriageStore store = new FakeTriageStore();
	private final ChangePatientStatusService service = new ChangePatientStatusService(store, store, store, store, store,
			store, new StatusTransitionPolicy());
	private final UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
	private final Instant registeredAt = Instant.parse("2026-11-18T15:00:00Z");

	@BeforeEach
	void storeARegisteredPatient() {
		store.patients.put(id, patient(PatientStatus.REGISTERED));
	}

	private Patient patient(PatientStatus status) {
		return new Patient(id, "Ana Gomez", 54,
				new VitalSigns(72, 120, 80, 99, new BigDecimal("36.5"), 2, 15), "Herida por cortar",
				TriageLevel.NOT_URGENT, 60, status, false, null, registeredAt, registeredAt, null);
	}

	@Test
	void startsTheConsultationAndAuditsIt() {
		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.assertNext(patient -> {
					assertThat(patient.status()).isEqualTo(PatientStatus.IN_CONSULTATION);
					assertThat(patient.statusChangedAt()).isEqualTo(store.now);
					assertThat(patient.consultationStartedAt()).isEqualTo(store.now);
				})
				.verifyComplete();

		assertThat(store.audit).singleElement().satisfies((AuditEntry entry) -> {
			assertThat(entry.actor()).isEqualTo("doctor");
			assertThat(entry.eventType()).isEqualTo(AuditEventType.CONSULTATION_STARTED);
			assertThat(entry.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
			assertThat(entry.newStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
			assertThat(entry.occurredAt()).isEqualTo(store.now);
		});
	}

	@Test
	void publishesAnEventPerTransitionWithTheLevelAndBothStatuses() {
		service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor").block();
		service.changeStatus(id, PatientStatus.DISCHARGED, "doctor").block();

		assertThat(store.events).hasSize(2);
		assertThat(store.events.get(0).type()).isEqualTo(PatientEventType.CONSULTATION_STARTED);
		assertThat(store.events.get(0).previousStatus()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(store.events.get(0).newStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
		assertThat(store.events.get(0).level()).isEqualTo(TriageLevel.NOT_URGENT);
		assertThat(store.events.get(1).type()).isEqualTo(PatientEventType.DISCHARGED);
		assertThat(store.events.get(1).previousStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
		assertThat(store.events.get(1).patientId()).isEqualTo(id);
	}

	@Test
	void publishesNothingWhenTheChangeIsRejected() {
		store.patients.put(id, patient(PatientStatus.DISCHARGED));

		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.expectError(InvalidStatusTransitionException.class)
				.verify();

		assertThat(store.events).isEmpty();
	}

	@Test
	void changeIsRolledBackWhenTheEventCannotBeRegistered() {
		store.failEvents = true;

		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.expectErrorMessage("outbox unavailable")
				.verify();

		assertThat(store.patients.get(id).status()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(store.audit).isEmpty();
		assertThat(store.events).isEmpty();
	}

	@Test
	void dischargesDirectlyFromRegisteredWithoutConsultationTime() {
		StepVerifier.create(service.changeStatus(id, PatientStatus.DISCHARGED, "nurse"))
				.assertNext(patient -> {
					assertThat(patient.status()).isEqualTo(PatientStatus.DISCHARGED);
					assertThat(patient.consultationStartedAt()).isNull();
				})
				.verifyComplete();

		assertThat(store.audit).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(AuditEventType.DISCHARGED);
			assertThat(entry.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
		});
	}

	@Test
	void dischargesAPatientInConsultation() {
		store.patients.put(id, patient(PatientStatus.IN_CONSULTATION));

		service.changeStatus(id, PatientStatus.DISCHARGED, "doctor").block();

		assertThat(store.patients.get(id).status()).isEqualTo(PatientStatus.DISCHARGED);
		assertThat(store.audit).singleElement().satisfies(entry -> {
			assertThat(entry.previousStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
			assertThat(entry.newStatus()).isEqualTo(PatientStatus.DISCHARGED);
		});
	}

	@Test
	void rejectsAnInvalidTransitionWithoutChangingOrAuditing() {
		store.patients.put(id, patient(PatientStatus.DISCHARGED));

		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.expectError(InvalidStatusTransitionException.class)
				.verify();

		assertThat(store.patients.get(id).status()).isEqualTo(PatientStatus.DISCHARGED);
		assertThat(store.audit).isEmpty();
	}

	@Test
	void rejectsRepeatingTheCurrentStatus() {
		StepVerifier.create(service.changeStatus(id, PatientStatus.REGISTERED, "nurse"))
				.expectError(InvalidStatusTransitionException.class)
				.verify();

		assertThat(store.audit).isEmpty();
	}

	@Test
	void unknownPatientIsNotFound() {
		StepVerifier.create(service.changeStatus(UUID.randomUUID(), PatientStatus.DISCHARGED, "nurse"))
				.expectError(PatientNotFoundException.class)
				.verify();

		assertThat(store.audit).isEmpty();
	}

	@Test
	void concurrentChangeLeavesNoAuditEntryAndReportsAConflict() {
		// Another request discharges the patient after this one read it and before it updates it.
		store.beforeUnitOfWork = () -> store.patients.put(id, patient(PatientStatus.DISCHARGED));

		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.expectError(ConcurrentStatusChangeException.class)
				.verify();

		assertThat(store.patients.get(id).status()).isEqualTo(PatientStatus.DISCHARGED);
		assertThat(store.audit).isEmpty();
	}

	@Test
	void patientThatDisappearsAfterLosingTheRaceIsReportedAsNotFound() {
		store.beforeUnitOfWork = () -> store.patients.remove(id);

		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.expectError(PatientNotFoundException.class)
				.verify();

		assertThat(store.audit).isEmpty();
	}

	@Test
	void changeIsRolledBackWhenTheAuditEntryCannotBeWritten() {
		store.failAudit = true;

		StepVerifier.create(service.changeStatus(id, PatientStatus.IN_CONSULTATION, "doctor"))
				.expectErrorMessage("audit store unavailable")
				.verify();

		assertThat(store.patients.get(id).status()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(store.audit).isEmpty();
	}

}
