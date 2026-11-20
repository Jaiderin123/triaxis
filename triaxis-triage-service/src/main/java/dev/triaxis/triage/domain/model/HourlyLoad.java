package dev.triaxis.triage.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One UTC hour of the operation report (RF-05). Patients count in the hour of their registration; the waits
 * only consider those who entered consultation and are {@code null} when none did (ASSUMPTIONS A43).
 *
 * @param hour                the start of the UTC hour
 * @param total               patients registered in the hour
 * @param byLevel             patients per level, every level present
 * @param averageWaitMinutes  mean minutes from registration to consultation, or null
 * @param p95WaitMinutes      95th percentile of those minutes (interpolated), or null
 * @param cumulativeTotal     patients registered from the start of the range up to this hour
 * @param loadRank            rank of the hour by number of patients (1 is the busiest; ties share the rank)
 */
public record HourlyLoad(Instant hour, long total, Map<TriageLevel, Long> byLevel, BigDecimal averageWaitMinutes,
		BigDecimal p95WaitMinutes, long cumulativeTotal, int loadRank) {

	public HourlyLoad {
		Objects.requireNonNull(hour, "hour");
		if (!hour.truncatedTo(ChronoUnit.HOURS).equals(hour)) {
			throw new IllegalArgumentException("A report bucket starts at a whole hour: " + hour);
		}
		EnumMap<TriageLevel, Long> complete = new EnumMap<>(TriageLevel.class);
		long sum = 0;
		for (TriageLevel level : TriageLevel.values()) {
			long count = byLevel == null ? 0 : byLevel.getOrDefault(level, 0L);
			requireNotNegative(count, "level count");
			complete.put(level, count);
			sum += count;
		}
		if (sum != total) {
			throw new IllegalArgumentException("The counts per level must add up to the total of the hour");
		}
		requireNotNegative(cumulativeTotal, "cumulative total");
		if (averageWaitMinutes != null && averageWaitMinutes.signum() < 0
				|| p95WaitMinutes != null && p95WaitMinutes.signum() < 0) {
			throw new IllegalArgumentException("A waiting time is never negative");
		}
		if (loadRank < 1) {
			throw new IllegalArgumentException("The load rank starts at 1");
		}
		byLevel = Collections.unmodifiableMap(complete);
	}

	private static void requireNotNegative(long value, String what) {
		if (value < 0) {
			throw new IllegalArgumentException("The " + what + " is never negative");
		}
	}

}
