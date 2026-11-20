package dev.triaxis.triage.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.triaxis.triage.domain.exception.FieldViolation;
import dev.triaxis.triage.domain.exception.InvalidReportRangeException;

/**
 * The period of the operation report: {@code from} is included, {@code to} is excluded (ASSUMPTIONS A42). It is
 * never empty and never longer than the maximum length, which bounds the number of hourly buckets.
 */
public record ReportRange(Instant from, Instant to) {

	/**
	 * @throws InvalidReportRangeException listing every problem, with the fields {@code from} and {@code to}
	 */
	public static ReportRange of(Instant from, Instant to, Duration maxLength) {
		if (maxLength == null || maxLength.isZero() || maxLength.isNegative()) {
			throw new IllegalArgumentException("The maximum length of a report range must be positive");
		}
		List<FieldViolation> violations = new ArrayList<>();
		if (from == null) {
			violations.add(new FieldViolation("from", "is required"));
		}
		if (to == null) {
			violations.add(new FieldViolation("to", "is required"));
		}
		if (from != null && to != null) {
			if (!to.isAfter(from)) {
				violations.add(new FieldViolation("to", "must be after the start of the range"));
			}
			else if (Duration.between(from, to).compareTo(maxLength) > 0) {
				violations.add(new FieldViolation("to", "the range must be at most " + describe(maxLength)));
			}
		}
		if (!violations.isEmpty()) {
			throw new InvalidReportRangeException(violations);
		}
		return new ReportRange(from, to);
	}

	public boolean contains(Instant instant) {
		return !instant.isBefore(from) && instant.isBefore(to);
	}

	private static String describe(Duration length) {
		return length.toHoursPart() == 0 && length.toMinutesPart() == 0 && length.toSecondsPart() == 0
				? length.toDays() + " days" : length.toString();
	}

}
