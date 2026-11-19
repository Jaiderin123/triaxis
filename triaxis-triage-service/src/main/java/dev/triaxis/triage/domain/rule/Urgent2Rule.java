package dev.triaxis.triage.domain.rule;

import java.math.BigDecimal;

import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;

/** Level 3: HR 90 to 120, temperature from 38.0 up to (not including) 38.5, or pain 7 or more. */
public class Urgent2Rule implements TriageLevelRule {

	private static final BigDecimal FEVER = new BigDecimal("38.0");
	private static final BigDecimal HIGH_FEVER = new BigDecimal("38.5");

	@Override
	public TriageLevel level() {
		return TriageLevel.URGENT_2;
	}

	@Override
	public boolean applies(VitalSigns v) {
		return (v.heartRate() >= 90 && v.heartRate() <= 120)
				|| (v.temperature().compareTo(FEVER) >= 0 && v.temperature().compareTo(HIGH_FEVER) < 0)
				|| v.pain() >= 7;
	}

}
