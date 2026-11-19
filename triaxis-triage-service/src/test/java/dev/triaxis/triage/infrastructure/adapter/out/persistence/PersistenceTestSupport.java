package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.service.ComplaintPolicy;
import dev.triaxis.triage.domain.service.SlaPolicy;
import dev.triaxis.triage.domain.service.TriageClassifier;

final class PersistenceTestSupport {

	private static final TriageClassifier CLASSIFIER = TriageClassifier.withDefaultRules(new ComplaintPolicy(Set.of()));

	private static final SlaPolicy SLA = slaPolicy();

	private PersistenceTestSupport() {
	}

	/** Registered patient with a fresh id; the time is truncated to microseconds, the precision of PostgreSQL. */
	static Patient newPatient() {
		RegistrationData data = new RegistrationData("Ana Gomez", 54, 130, 180, 110, 97, new BigDecimal("36.8"), 8, 15,
				"Dolor toracico");
		return Patient.register(UUID.randomUUID(), data, CLASSIFIER, SLA, Instant.now().truncatedTo(ChronoUnit.MICROS));
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
