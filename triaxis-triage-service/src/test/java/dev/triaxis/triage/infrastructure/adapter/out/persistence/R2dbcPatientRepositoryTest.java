package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class R2dbcPatientRepositoryTest {

	@Autowired
	R2dbcPatientRepository repository;

	private static Instant later() {
		return Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS);
	}

	@Test
	void savedPatientIsReadBackIdentical() {
		Patient patient = PersistenceTestSupport.newPatient();

		StepVerifier.create(repository.save(patient)).expectNext(patient).verifyComplete();
		StepVerifier.create(repository.findById(patient.id())).expectNext(patient).verifyComplete();
	}

	@Test
	void unknownPatientIsNotFound() {
		StepVerifier.create(repository.findById(UUID.randomUUID())).verifyComplete();
	}

	@Test
	void startingTheConsultationRecordsStatusAndConsultationTime() {
		Patient patient = PersistenceTestSupport.newPatient();
		Instant now = later();
		repository.save(patient).block();

		StepVerifier.create(repository.updateStatus(patient.id(), PatientStatus.REGISTERED,
						PatientStatus.IN_CONSULTATION, now))
				.assertNext(updated -> {
					assertThat(updated.status()).isEqualTo(PatientStatus.IN_CONSULTATION);
					assertThat(updated.statusChangedAt()).isEqualTo(now);
					assertThat(updated.consultationStartedAt()).isEqualTo(now);
					assertThat(updated.registeredAt()).isEqualTo(patient.registeredAt());
					assertThat(updated.level()).isEqualTo(patient.level());
				})
				.verifyComplete();
	}

	/** A system clock moved back between the registration and the consultation never gives a negative wait. */
	@Test
	void theConsultationNeverStartsBeforeTheRegistration() {
		Patient patient = PersistenceTestSupport.newPatient();
		repository.save(patient).block();
		Instant before = patient.registeredAt().minusSeconds(30);

		StepVerifier.create(repository.updateStatus(patient.id(), PatientStatus.REGISTERED,
						PatientStatus.IN_CONSULTATION, before))
				.assertNext(updated -> assertThat(updated.consultationStartedAt()).isEqualTo(patient.registeredAt()))
				.verifyComplete();
	}

	@Test
	void directDischargeLeavesNoConsultationTime() {
		Patient patient = PersistenceTestSupport.newPatient();
		repository.save(patient).block();

		StepVerifier.create(repository.updateStatus(patient.id(), PatientStatus.REGISTERED,
						PatientStatus.DISCHARGED, later()))
				.assertNext(updated -> {
					assertThat(updated.status()).isEqualTo(PatientStatus.DISCHARGED);
					assertThat(updated.consultationStartedAt()).isNull();
				})
				.verifyComplete();
	}

	@Test
	void consultationTimeSurvivesTheDischarge() {
		Patient patient = PersistenceTestSupport.newPatient();
		Instant started = later();
		repository.save(patient).block();
		repository.updateStatus(patient.id(), PatientStatus.REGISTERED, PatientStatus.IN_CONSULTATION, started).block();

		StepVerifier.create(repository.updateStatus(patient.id(), PatientStatus.IN_CONSULTATION,
						PatientStatus.DISCHARGED, started.plusSeconds(600)))
				.assertNext(updated -> assertThat(updated.consultationStartedAt()).isEqualTo(started))
				.verifyComplete();
	}

	@Test
	void updateIsIgnoredWhenTheStatusIsNotTheExpectedOne() {
		Patient patient = PersistenceTestSupport.newPatient();
		repository.save(patient).block();

		StepVerifier.create(repository.updateStatus(patient.id(), PatientStatus.IN_CONSULTATION,
						PatientStatus.DISCHARGED, later()))
				.verifyComplete();
		assertThat(repository.findById(patient.id()).block().status()).isEqualTo(PatientStatus.REGISTERED);
	}

	@Test
	void updateOfAnUnknownPatientMatchesNothing() {
		StepVerifier.create(repository.updateStatus(UUID.randomUUID(), PatientStatus.REGISTERED,
						PatientStatus.DISCHARGED, later()))
				.verifyComplete();
	}

}
