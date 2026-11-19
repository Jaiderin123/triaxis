package dev.triaxis.triage.domain.rule;

import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;

/** Level 1: HR below 40 or above 120, SpO2 below 90, systolic below 90 or GCS 8 or lower. */
public class CriticalRule implements TriageLevelRule {

	@Override
	public TriageLevel level() {
		return TriageLevel.CRITICAL;
	}

	@Override
	public boolean applies(VitalSigns v) {
		return v.heartRate() < 40 || v.heartRate() > 120
				|| v.spo2() < 90
				|| v.systolic() < 90
				|| v.gcs() <= 8;
	}

}
