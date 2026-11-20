package dev.triaxis.assistant.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Active patients per level (RF-04 B). Every level is present, with zero when nobody waits at it.
 *
 * @param updatedAt  time of the newest applied event, or null when none was applied
 */
public record UrgencyView(Map<TriageLevel, Long> active, Instant updatedAt) {

	public UrgencyView {
		EnumMap<TriageLevel, Long> complete = new EnumMap<>(TriageLevel.class);
		for (TriageLevel level : TriageLevel.values()) {
			long count = active == null ? 0 : active.getOrDefault(level, 0L);
			if (count < 0) {
				throw new IllegalArgumentException("A level cannot have a negative count: " + level);
			}
			complete.put(level, count);
		}
		active = Collections.unmodifiableMap(complete);
	}

	public static UrgencyView empty() {
		return new UrgencyView(Map.of(), null);
	}

	public long total() {
		return active.values().stream().mapToLong(Long::longValue).sum();
	}

}
