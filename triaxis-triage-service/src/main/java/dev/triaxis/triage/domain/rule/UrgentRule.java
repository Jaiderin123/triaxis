package dev.triaxis.triage.domain.rule;

import java.math.BigDecimal;

import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;

/** Level 2: SpO2 90 to 93, systolic 90 to 100, temperature 38.5 or more, or GCS 9 to 13. */
public class UrgentRule implements TriageLevelRule {

	private static final BigDecimal HIGH_FEVER = new BigDecimal("38.5");

	@Override
	public TriageLevel level() {
		return TriageLevel.URGENT;
	}

	@Override
	public boolean applies(VitalSigns v) {
		return between(v.spo2(), 90, 93)
				|| between(v.systolic(), 90, 100)
				|| v.temperature().compareTo(HIGH_FEVER) >= 0
				|| between(v.gcs(), 9, 13);
	}

	private static boolean between(int value, int min, int max) {
		return value >= min && value <= max;
	}

}
