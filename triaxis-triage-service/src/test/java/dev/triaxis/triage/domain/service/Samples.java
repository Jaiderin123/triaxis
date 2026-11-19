package dev.triaxis.triage.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.model.TriageLevel;

/** Shared test fixtures. */
final class Samples {

	static final Instant NOW = Instant.parse("2026-11-18T15:30:00Z");

	private Samples() {
	}

	static ComplaintPolicy complaints() {
		return new ComplaintPolicy(Set.of("documentos", "recoleccion de resultados", "renovacion de recetario",
				"vacunacion", "seguimiento"));
	}

	static SlaPolicy sla() {
		Map<TriageLevel, Integer> minutes = new EnumMap<>(TriageLevel.class);
		minutes.put(TriageLevel.CRITICAL, 5);
		minutes.put(TriageLevel.URGENT, 10);
		minutes.put(TriageLevel.URGENT_2, 30);
		minutes.put(TriageLevel.NOT_URGENT, 60);
		minutes.put(TriageLevel.NOT_URGENT_2, 120);
		return new SlaPolicy(minutes);
	}

	static TriageClassifier classifier() {
		return TriageClassifier.withDefaultRules(complaints());
	}

	/** A healthy adult with a clinical complaint: classified as NOT_URGENT. */
	static RegistrationData healthy() {
		return new RegistrationData("Ana Gomez", 30, 72, 120, 80, 99, new BigDecimal("36.5"), 2, 15,
				"Herida por cortar");
	}

	static RegistrationData with(UnaryOperator<Builder> change) {
		return change.apply(new Builder(healthy())).build();
	}

	static Patient register(RegistrationData data) {
		return Patient.register(UUID.randomUUID(), data, classifier(), sla(), NOW);
	}

	static final class Builder {

		String fullName;
		Integer age;
		Integer heartRate;
		Integer systolic;
		Integer diastolic;
		Integer spo2;
		BigDecimal temperature;
		Integer pain;
		Integer gcs;
		String complaint;

		Builder(RegistrationData d) {
			fullName = d.fullName();
			age = d.age();
			heartRate = d.heartRate();
			systolic = d.systolic();
			diastolic = d.diastolic();
			spo2 = d.spo2();
			temperature = d.temperature();
			pain = d.pain();
			gcs = d.gcs();
			complaint = d.chiefComplaint();
		}

		Builder fullName(String v) { fullName = v; return this; }
		Builder age(Integer v) { age = v; return this; }
		Builder heartRate(Integer v) { heartRate = v; return this; }
		Builder systolic(Integer v) { systolic = v; return this; }
		Builder diastolic(Integer v) { diastolic = v; return this; }
		Builder spo2(Integer v) { spo2 = v; return this; }
		Builder temperature(String v) { temperature = v == null ? null : new BigDecimal(v); return this; }
		Builder pain(Integer v) { pain = v; return this; }
		Builder gcs(Integer v) { gcs = v; return this; }
		Builder complaint(String v) { complaint = v; return this; }

		RegistrationData build() {
			return new RegistrationData(fullName, age, heartRate, systolic, diastolic, spo2, temperature, pain, gcs,
					complaint);
		}

	}

}
