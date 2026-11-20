package dev.triaxis.triage.infrastructure.adapter.in.web.validation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Reads {@code desde} and {@code hasta} (ASSUMPTIONS A42). A date is a whole UTC day: as a start it is the start
 * of the day, as an end it includes the day (the start of the next one, since the end is excluded). An instant
 * needs a zone ({@code Z} or an offset); a date-time without a zone is ambiguous and rejected. A missing value is
 * left to the domain, which reports it with the other problems of the range.
 */
public final class ReportDateParser {

	private ReportDateParser() {
	}

	public static Instant start(String value, String field) {
		return parse(value, field, false);
	}

	public static Instant end(String value, String field) {
		return parse(value, field, true);
	}

	private static Instant parse(String value, String field, boolean end) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.strip();
		if (text.length() == 10) {
			try {
				LocalDate day = LocalDate.parse(text);
				return (end ? day.plusDays(1) : day).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
			catch (DateTimeParseException notADate) {
				throw new InvalidRequestException(field, "must be an ISO 8601 date (2026-11-19) or an instant with a "
						+ "time zone (2026-11-19T15:00:00Z)");
			}
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException notAnInstant) {
			if (isLocalDateTime(text)) {
				throw new InvalidRequestException(field, "needs a time zone, for example Z or -05:00");
			}
			throw new InvalidRequestException(field, "must be an ISO 8601 date (2026-11-19) or an instant with a "
					+ "time zone (2026-11-19T15:00:00Z)");
		}
	}

	private static boolean isLocalDateTime(String text) {
		try {
			LocalDateTime.parse(text);
			return true;
		}
		catch (DateTimeParseException e) {
			return false;
		}
	}

}
