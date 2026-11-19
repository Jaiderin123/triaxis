package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.util.StreamUtils;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TriageIntegrationTest
class AuditAndSchemaTest {

	@Autowired
	R2dbcPatientRepository patients;

	@Autowired
	R2dbcAuditRepository audit;

	@Autowired
	TransactionalUnitOfWork unitOfWork;

	@Autowired
	DatabaseClient client;

	private AuditEntry entryFor(Patient patient) {
		return new AuditEntry(patient.id(), "nurse", AuditEventType.REGISTERED, null, PatientStatus.REGISTERED, false,
				Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	private long auditRows(UUID patientId) {
		return client.sql("SELECT count(*) AS total FROM patient_audit WHERE patient_id = :id")
				.bind("id", patientId)
				.map(row -> row.get("total", Long.class))
				.one().block();
	}

	@Test
	void auditEntryIsStoredWithItsActorAndStatuses() {
		Patient patient = PersistenceTestSupport.newPatient();
		patients.save(patient).block();
		AuditEntry entry = entryFor(patient);

		StepVerifier.create(audit.append(entry)).expectNext(entry).verifyComplete();

		client.sql("SELECT actor, event_type, previous_status, new_status FROM patient_audit WHERE patient_id = :id")
				.bind("id", patient.id())
				.map(row -> row.get("actor", String.class) + "|" + row.get("event_type", String.class) + "|"
						+ row.get("previous_status", String.class) + "|" + row.get("new_status", String.class))
				.one()
				.as(StepVerifier::create)
				.expectNext("nurse|REGISTERED|null|REGISTERED")
				.verifyComplete();
	}

	@Test
	void auditRowsCannotBeUpdated() {
		Patient patient = PersistenceTestSupport.newPatient();
		patients.save(patient).block();
		audit.append(entryFor(patient)).block();

		assertThatThrownBy(() -> client.sql("UPDATE patient_audit SET actor = 'someone' WHERE patient_id = :id")
				.bind("id", patient.id()).fetch().rowsUpdated().block())
				.hasMessageContaining("append-only");
	}

	@Test
	void auditRowsCannotBeDeleted() {
		Patient patient = PersistenceTestSupport.newPatient();
		patients.save(patient).block();
		audit.append(entryFor(patient)).block();

		assertThatThrownBy(() -> client.sql("DELETE FROM patient_audit WHERE patient_id = :id")
				.bind("id", patient.id()).fetch().rowsUpdated().block())
				.hasMessageContaining("append-only");
		assertThat(auditRows(patient.id())).isEqualTo(1);
	}

	@Test
	void auditTableCannotBeTruncated() {
		assertThatThrownBy(() -> client.sql("TRUNCATE TABLE patient_audit").fetch().rowsUpdated().block())
				.hasMessageContaining("append-only");
	}

	@Test
	void auditForAnUnknownPatientIsRejected() {
		Patient neverSaved = PersistenceTestSupport.newPatient();

		StepVerifier.create(audit.append(entryFor(neverSaved))).expectError().verify();
	}

	@Test
	void patientAndAuditAreCommittedTogether() {
		Patient patient = PersistenceTestSupport.newPatient();

		unitOfWork.execute(patients.save(patient).flatMap(saved -> audit.append(entryFor(saved)))).block();

		assertThat(patients.findById(patient.id()).block()).isEqualTo(patient);
		assertThat(auditRows(patient.id())).isEqualTo(1);
	}

	@Test
	void patientIsRolledBackWhenTheAuditEntryFails() {
		Patient patient = PersistenceTestSupport.newPatient();
		Patient other = PersistenceTestSupport.newPatient();

		// The audit entry points to a patient that does not exist, so the foreign key fails after the insert.
		StepVerifier.create(unitOfWork.execute(patients.save(patient).then(audit.append(entryFor(other)))))
				.expectError().verify();

		assertThat(patients.findById(patient.id()).block()).isNull();
	}

	@Test
	void databaseRefusesValuesOutsideTheRangesEvenWithoutTheDomain() {
		assertThatThrownBy(() -> client.sql("""
				INSERT INTO patients (id, full_name, age, heart_rate, systolic, diastolic, spo2, temperature, pain, gcs,
				  chief_complaint, level, sla_minutes, status, registered_at, status_changed_at)
				VALUES (:id, 'Ana Gomez', 30, 300, 120, 80, 99, 36.5, 2, 15, 'x', 'NOT_URGENT', 60, 'REGISTERED', now(), now())
				""").bind("id", UUID.randomUUID()).fetch().rowsUpdated().block())
				.hasMessageContaining("chk_patients_heart_rate");
	}

	@Test
	void schemaScriptCanRunAgainWithoutErrorsAndKeepsTheProtection() throws Exception {
		String script = StreamUtils.copyToString(new ClassPathResource("db/schema.sql").getInputStream(),
				StandardCharsets.UTF_8);

		client.sql(script).fetch().rowsUpdated().block();
		client.sql(script).fetch().rowsUpdated().block();

		assertThatThrownBy(() -> client.sql("TRUNCATE TABLE patient_audit").fetch().rowsUpdated().block())
				.hasMessageContaining("append-only");
	}

}
