package dev.triaxis.triage.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EscalateOverduePatientsServiceTest {

	private final FakeTriageStore store = new FakeTriageStore();
	private final Instant base = Instant.parse("2026-11-18T15:00:00Z");

	private EscalateOverduePatientsService service(int batchSize) {
		return new EscalateOverduePatientsService(store, store, store, store, store, store, store, batchSize);
	}

	/** Stores a level 1 patient (SLA of 5 minutes) registered at the given time. */
	private Patient store(int number, Instant registeredAt, PatientStatus status) {
		UUID id = UUID.fromString("00000000-0000-0000-0000-%012d".formatted(number));
		Patient patient = new Patient(id, "Paciente " + number, 54,
				new VitalSigns(130, 180, 110, 97, new BigDecimal("36.8"), 8, 15), "Dolor toracico",
				TriageLevel.CRITICAL, 5, status, false, null, registeredAt, registeredAt, null);
		store.patients.put(id, patient);
		return patient;
	}

	@Test
	void escalatesAnOverduePatientWithAuditEventAndMetric() {
		Patient patient = store(1, base, PatientStatus.REGISTERED);
		store.now = base.plusSeconds(301);

		StepVerifier.create(service(100).escalateOverdue()).expectNext(1).verifyComplete();

		Patient escalated = store.patients.get(patient.id());
		assertThat(escalated.escalated()).isTrue();
		assertThat(escalated.escalatedAt()).isEqualTo(store.now);
		assertThat(escalated.status()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(escalated.statusChangedAt()).isEqualTo(base);
		assertThat(store.audit).singleElement().satisfies((AuditEntry entry) -> {
			assertThat(entry.actor()).isEqualTo(AuditEntry.SYSTEM_ACTOR);
			assertThat(entry.eventType()).isEqualTo(AuditEventType.ESCALATED);
			assertThat(entry.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
			assertThat(entry.newStatus()).isEqualTo(PatientStatus.REGISTERED);
			assertThat(entry.escalated()).isTrue();
			assertThat(entry.occurredAt()).isEqualTo(store.now);
		});
		assertThat(store.events).singleElement().satisfies((PatientEvent event) -> {
			assertThat(event.type()).isEqualTo(PatientEventType.ESCALATED);
			assertThat(event.patientId()).isEqualTo(patient.id());
			assertThat(event.level()).isEqualTo(TriageLevel.CRITICAL);
			assertThat(event.escalated()).isTrue();
		});
		assertThat(store.escalatedLevels).containsExactly(TriageLevel.CRITICAL);
		assertThat(store.committedUnits).hasValue(1);
	}

	@Test
	void aSecondRunDoesNothing() {
		store(1, base, PatientStatus.REGISTERED);
		store.now = base.plusSeconds(301);
		EscalateOverduePatientsService service = service(100);

		service.escalateOverdue().block();
		store.now = store.now.plusSeconds(3600);
		StepVerifier.create(service.escalateOverdue()).expectNext(0).verifyComplete();

		assertThat(store.audit).hasSize(1);
		assertThat(store.events).hasSize(1);
		assertThat(store.escalatedLevels).hasSize(1);
	}

	@Test
	void theLimitIsStrict() {
		Patient patient = store(1, base, PatientStatus.REGISTERED);
		store.now = base.plusSeconds(300);

		StepVerifier.create(service(100).escalateOverdue()).expectNext(0).verifyComplete();
		assertThat(store.patients.get(patient.id()).escalated()).isFalse();

		store.now = base.plusSeconds(300).plusNanos(1000);
		StepVerifier.create(service(100).escalateOverdue()).expectNext(1).verifyComplete();
	}

	@Test
	void patientsInConsultationOrDischargedAreNeverEscalated() {
		store(1, base, PatientStatus.IN_CONSULTATION);
		store(2, base, PatientStatus.DISCHARGED);
		store.now = base.plusSeconds(3600);

		StepVerifier.create(service(100).escalateOverdue()).expectNext(0).verifyComplete();

		assertThat(store.patients.values()).noneMatch(Patient::escalated);
		assertThat(store.audit).isEmpty();
		assertThat(store.events).isEmpty();
	}

	@Test
	void escalatesTheOldestFirstAndRespectsTheBatchSize() {
		Patient newest = store(1, base.plusSeconds(20), PatientStatus.REGISTERED);
		Patient oldest = store(2, base, PatientStatus.REGISTERED);
		Patient middle = store(3, base.plusSeconds(10), PatientStatus.REGISTERED);
		store.now = base.plusSeconds(3600);

		StepVerifier.create(service(2).escalateOverdue()).expectNext(2).verifyComplete();

		assertThat(store.events).extracting(PatientEvent::patientId).containsExactly(oldest.id(), middle.id());
		assertThat(store.patients.get(newest.id()).escalated()).isFalse();
		StepVerifier.create(service(2).escalateOverdue()).expectNext(1).verifyComplete();
		assertThat(store.patients.get(newest.id()).escalated()).isTrue();
	}

	@Test
	void everythingIsRolledBackWhenTheEventCannotBeRegistered() {
		Patient patient = store(1, base, PatientStatus.REGISTERED);
		store.now = base.plusSeconds(301);
		store.failEvents = true;

		StepVerifier.create(service(100).escalateOverdue()).expectErrorMessage("outbox unavailable").verify();

		assertThat(store.patients.get(patient.id()).escalated()).isFalse();
		assertThat(store.audit).isEmpty();
		assertThat(store.events).isEmpty();
		assertThat(store.escalatedLevels).isEmpty();
	}

	@Test
	void everythingIsRolledBackWhenTheAuditEntryCannotBeWritten() {
		Patient patient = store(1, base, PatientStatus.REGISTERED);
		store.now = base.plusSeconds(301);
		store.failAudit = true;

		StepVerifier.create(service(100).escalateOverdue()).expectErrorMessage("audit store unavailable").verify();

		assertThat(store.patients.get(patient.id()).escalated()).isFalse();
		assertThat(store.events).isEmpty();
	}

	@Test
	void theBatchSizeMustBePositive() {
		assertThatThrownBy(() -> service(0)).isInstanceOf(IllegalArgumentException.class);
	}

}
