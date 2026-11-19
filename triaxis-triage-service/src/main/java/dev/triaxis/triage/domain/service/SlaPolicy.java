package dev.triaxis.triage.domain.service;

import java.util.EnumMap;
import java.util.Map;

import dev.triaxis.triage.domain.model.TriageLevel;

/** Maximum minutes from registration to consultation, per level. */
public class SlaPolicy {

	private final Map<TriageLevel, Integer> minutes;

	public SlaPolicy(Map<TriageLevel, Integer> minutesPerLevel) {
		EnumMap<TriageLevel, Integer> copy = new EnumMap<>(TriageLevel.class);
		for (TriageLevel level : TriageLevel.values()) {
			Integer value = minutesPerLevel.get(level);
			if (value == null || value <= 0) {
				throw new IllegalArgumentException("SLA minutes must be a positive number for " + level);
			}
			copy.put(level, value);
		}
		this.minutes = copy;
	}

	public int minutesFor(TriageLevel level) {
		return minutes.get(level);
	}

}
