package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.service.ChangePatientStatusService;
import dev.triaxis.triage.application.service.EscalateOverduePatientsService;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.service.StatusTransitionPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The escalation statement and use case against a real PostgreSQL. Patients are back-dated to a test epoch
 * and the clock is fixed, so no test waits for an SLA to expire.
 */
@TriageIntegrationTest
class EscalationIntegrationTest {

	@Autowired
	R2dbcPatientRepository patients;

	@Autowired
	R2dbcAuditRepository audit;

	@Autowired
	TransactionalUnitOfWork unitOfWork;

	@Autowired
	EventPublisher events;

	@Autowired
	IdGenerator ids;

	@Autowired
	TriageMetrics metrics;

	@Autowired
	MeterRegistry meters;

	@Autowired
	DatabaseClient database;

	private EscalateOverduePatientsService escalation(ClockPort clock, int batchSize) {
		return new EscalateOverduePatientsService(patients, audit, events, ids, clock, unitOfWork, metrics, batchSize);
	}

	private Patient saved(Instant registeredAt, PatientStatus status) {
		return patients.save(PersistenceTestSupport.registeredAt(registeredAt, status)).block();
	}

	private long auditEntries(UUID id, String type) {
		return database.sql("SELECT count(*) AS total FROM patient_audit WHERE patient_id = :id AND event_type = :type")
				.bind("id", id).bind("type", type).map(row -> row.get("total", Long.class)).one().block();
	}

	private long outboxEvents(UUID id) {
		return database.sql("""
				SELECT count(*) AS total FROM outbox_events WHERE message_key = :key AND event_type = 'paciente_escalado'
				""").bind("key", id.toString()).map(row -> row.get("total", Long.class)).one().block();
	}

	@Test
	void anOverduePatientIsEscalatedOnceAndASecondRunDoesNothing() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient patient = saved(epoch, PatientStatus.REGISTERED);
		Instant now = epoch.plus(Duration.ofDays(1));

		StepVerifier.create(escalation(() -> now, 100).escalateOverdue()).expectNext(1).verifyComplete();
		StepVerifier.create(escalation(() -> now.plusSeconds(60), 100).escalateOverdue()).expectNext(0)
				.verifyComplete();

		Patient stored = patients.findById(patient.id()).block();
		assertThat(stored.escalated()).isTrue();
		assertThat(stored.escalatedAt()).isEqualTo(now);
		assertThat(stored.status()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(stored.statusChangedAt()).isEqualTo(patient.statusChangedAt());
		assertThat(auditEntries(patient.id(), "ESCALATED")).isEqualTo(1);
		assertThat(outboxEvents(patient.id())).isEqualTo(1);
		String actor = database.sql("""
				SELECT actor FROM patient_audit WHERE patient_id = :id AND event_type = 'ESCALATED'
				""").bind("id", patient.id()).map(row -> row.get("actor", String.class)).one().block();
		assertThat(actor).isEqualTo("system");
	}

	@Test
	void patientsInConsultationDischargedOrAlreadyEscalatedAreNeverSelected() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Instant now = epoch.plus(Duration.ofDays(1));
		Patient inConsultation = saved(epoch, PatientStatus.IN_CONSULTATION);
		Patient discharged = saved(epoch, PatientStatus.DISCHARGED);
		Patient waiting = saved(epoch, PatientStatus.REGISTERED);
		escalation(() -> epoch.plusSeconds(301), 100).escalateOverdue().block();

		StepVerifier.create(patients.escalateOverdue(now, 100)).verifyComplete();

		assertThat(patients.findById(inConsultation.id()).block().escalated()).isFalse();
		assertThat(patients.findById(discharged.id()).block().escalated()).isFalse();
		assertThat(patients.findById(waiting.id()).block().escalatedAt()).isEqualTo(epoch.plusSeconds(301));
	}

	@Test
	void theLimitIsStrict() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient patient = saved(epoch, PatientStatus.REGISTERED);
		Instant limit = epoch.plus(Duration.ofMinutes(patient.slaMinutes()));

		StepVerifier.create(patients.escalateOverdue(limit, 100)).verifyComplete();
		StepVerifier.create(patients.escalateOverdue(limit.plusNanos(1000), 100))
				.assertNext(escalated -> assertThat(escalated.id()).isEqualTo(patient.id()))
				.verifyComplete();
	}

	@Test
	void theOldestAreEscalatedFirstAndTheBatchSizeIsRespected() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Instant now = epoch.plus(Duration.ofDays(1));
		Patient newest = saved(epoch.plusSeconds(20), PatientStatus.REGISTERED);
		Patient oldest = saved(epoch, PatientStatus.REGISTERED);
		Patient middle = saved(epoch.plusSeconds(10), PatientStatus.REGISTERED);

		StepVerifier.create(patients.escalateOverdue(now, 2))
				.assertNext(first -> assertThat(first.id()).isEqualTo(oldest.id()))
				.assertNext(second -> assertThat(second.id()).isEqualTo(middle.id()))
				.verifyComplete();
		assertThat(patients.findById(newest.id()).block().escalated()).isFalse();
	}

	@Test
	void patientAuditAndEventAreRolledBackTogether() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient patient = saved(epoch, PatientStatus.REGISTERED);
		EventPublisher failing = event -> events.publish(event)
				.then(Mono.error(new IllegalStateException("outbox unavailable")));
		EscalateOverduePatientsService service = new EscalateOverduePatientsService(patients, audit, failing, ids,
				() -> epoch.plus(Duration.ofDays(1)), unitOfWork, metrics, 100);

		StepVerifier.create(service.escalateOverdue()).expectErrorMessage("outbox unavailable").verify();

		assertThat(patients.findById(patient.id()).block().escalated()).isFalse();
		assertThat(auditEntries(patient.id(), "ESCALATED")).isZero();
		assertThat(outboxEvents(patient.id())).isZero();
	}

	@Test
	void twoConcurrentRunsEscalateEachPatientOnce() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Instant now = epoch.plus(Duration.ofDays(1));
		List<Patient> waiting = IntStream.range(0, 10)
				.mapToObj(i -> saved(epoch.plusSeconds(i), PatientStatus.REGISTERED)).toList();

		List<Integer> counts = Flux.merge(
				escalation(() -> now, 3).escalateOverdue().subscribeOn(Schedulers.parallel()).repeat(5),
				escalation(() -> now, 3).escalateOverdue().subscribeOn(Schedulers.parallel()).repeat(5))
				.collectList().block();

		assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
		for (Patient patient : waiting) {
			assertThat(auditEntries(patient.id(), "ESCALATED")).as("audit of %s", patient.id()).isEqualTo(1);
			assertThat(outboxEvents(patient.id())).as("events of %s", patient.id()).isEqualTo(1);
		}
	}

	/**
	 * RN-03 under a race: whichever commits first, an escalation entry never follows a consultation or discharge
	 * entry of the same patient, and a patient that entered consultation first is never escalated.
	 */
	@Test
	void aConcurrentStatusChangeAndTheEscalationKeepTheAuditConsistent() {
		ChangePatientStatusService change = new ChangePatientStatusService(patients, audit, events, ids, Instant::now,
				unitOfWork, new StatusTransitionPolicy());
		int escalatedFirst = 0;
		int changedFirst = 0;

		for (int round = 0; round < 25; round++) {
			Instant epoch = PersistenceTestSupport.nextEpoch();
			Patient patient = saved(epoch, PatientStatus.REGISTERED);
			PatientStatus target = round % 2 == 0 ? PatientStatus.IN_CONSULTATION : PatientStatus.DISCHARGED;

			Flux.merge(
					escalation(() -> epoch.plus(Duration.ofDays(1)), 100).escalateOverdue()
							.subscribeOn(Schedulers.parallel()).then(),
					change.changeStatus(patient.id(), target, "doctor").subscribeOn(Schedulers.parallel()).then())
					.then().block();

			List<String> history = database.sql("SELECT event_type FROM patient_audit WHERE patient_id = :id ORDER BY id")
					.bind("id", patient.id()).map(row -> row.get("event_type", String.class)).all().collectList()
					.block();
			Patient stored = patients.findById(patient.id()).block();
			assertThat(stored.status()).as("round %d", round).isEqualTo(target);
			if (history.contains("ESCALATED")) {
				assertThat(history).as("round %d", round).containsExactly("ESCALATED", target == PatientStatus.IN_CONSULTATION
						? "CONSULTATION_STARTED" : "DISCHARGED");
				assertThat(stored.escalated()).isTrue();
				escalatedFirst++;
			}
			else {
				assertThat(history).as("round %d", round).hasSize(1);
				assertThat(stored.escalated()).isFalse();
				assertThat(outboxEvents(patient.id())).isZero();
				changedFirst++;
			}
		}

		assertThat(escalatedFirst + changedFirst).isEqualTo(25);
	}

	@Test
	void theCounterIsIncrementedByLevel() {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		saved(epoch, PatientStatus.REGISTERED);
		saved(epoch.plusSeconds(1), PatientStatus.REGISTERED);
		double before = meters.counter("triage.escalations", "level", "CRITICAL").count();

		escalation(() -> epoch.plus(Duration.ofDays(1)), 100).escalateOverdue().block();

		assertThat(meters.counter("triage.escalations", "level", "CRITICAL").count()).isEqualTo(before + 2);
	}

}
