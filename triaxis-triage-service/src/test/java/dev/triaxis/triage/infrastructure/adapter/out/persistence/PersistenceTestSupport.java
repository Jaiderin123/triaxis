package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.service.ComplaintPolicy;
import dev.triaxis.triage.domain.service.SlaPolicy;
import dev.triaxis.triage.domain.service.TriageClassifier;

public final class PersistenceTestSupport {

	private static final TriageClassifier CLASSIFIER = TriageClassifier.withDefaultRules(new ComplaintPolicy(Set.of()));

	private static final SlaPolicy SLA = slaPolicy();

	/** Each escalation test gets its own epoch, earlier than every previous one (see {@link #nextEpoch()}). */
	private static final AtomicLong EPOCHS = new AtomicLong();

	private PersistenceTestSupport() {
	}

	/** Registered patient with a fresh id; the time is truncated to microseconds, the precision of PostgreSQL. */
	public static Patient newPatient() {
		RegistrationData data = new RegistrationData("Ana Gomez", 54, 130, 180, 110, 97, new BigDecimal("36.8"), 8, 15,
				"Dolor toracico");
		return Patient.register(UUID.randomUUID(), data, CLASSIFIER, SLA, Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	/**
	 * A point in the past, one thousand days earlier than the one returned by the previous call. Integration tests
	 * share one database, so an escalation test registers its patients just after its own epoch and escalates at
	 * {@code epoch + 1 day}: every patient left by an earlier test (or registered at the real time) is later than
	 * that instant and can never be selected.
	 */
	public static Instant nextEpoch() {
		return Instant.parse("2000-01-01T00:00:00Z").minus(Duration.ofDays(1000 * EPOCHS.incrementAndGet()));
	}

	/** A level 1 patient (SLA of 5 minutes) registered at the given time, in the given status. */
	public static Patient registeredAt(Instant registeredAt, PatientStatus status) {
		Patient fresh = newPatient();
		return new Patient(fresh.id(), fresh.fullName(), fresh.age(), fresh.vitalSigns(), fresh.chiefComplaint(),
				fresh.level(), fresh.slaMinutes(), status, false, null, registeredAt, registeredAt,
				status == PatientStatus.IN_CONSULTATION ? registeredAt : null);
	}

	private static SlaPolicy slaPolicy() {
		EnumMap<TriageLevel, Integer> minutes = new EnumMap<>(TriageLevel.class);
		minutes.put(TriageLevel.CRITICAL, 5);
		minutes.put(TriageLevel.URGENT, 10);
		minutes.put(TriageLevel.URGENT_2, 30);
		minutes.put(TriageLevel.NOT_URGENT, 60);
		minutes.put(TriageLevel.NOT_URGENT_2, 120);
		return new SlaPolicy(minutes);
	}

}
