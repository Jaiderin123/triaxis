package dev.triaxis.triage.domain.model;

import java.time.Duration;
import java.time.Instant;

import dev.triaxis.triage.domain.exception.FieldViolation;
import dev.triaxis.triage.domain.exception.InvalidReportRangeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ReportRangeTest {

	private static final Duration MAX = Duration.ofDays(31);
	private static final Instant FROM = Instant.parse("2026-11-19T00:00:00Z");

	@Test
	void aRangeKeepsItsLimitsAndIncludesOnlyTheStart() {
		ReportRange range = ReportRange.of(FROM, FROM.plusSeconds(3600), MAX);

		assertThat(range.from()).isEqualTo(FROM);
		assertThat(range.to()).isEqualTo(FROM.plusSeconds(3600));
		assertThat(range.contains(FROM)).isTrue();
		assertThat(range.contains(FROM.plusSeconds(3599))).isTrue();
		assertThat(range.contains(FROM.plusSeconds(3600))).isFalse();
		assertThat(range.contains(FROM.minusNanos(1000))).isFalse();
	}

	@Test
	void exactlyTheMaximumLengthIsAccepted() {
		assertThat(ReportRange.of(FROM, FROM.plus(MAX), MAX).to()).isEqualTo(FROM.plus(MAX));
	}

	@Test
	void longerThanTheMaximumIsRejectedOnTheEnd() {
		assertThat(violations(FROM, FROM.plus(MAX).plusSeconds(1)))
				.singleElement().satisfies(v -> {
					assertThat(v.field()).isEqualTo("to");
					assertThat(v.message()).contains("31 days");
				});
	}

	@Test
	void anEmptyOrInvertedRangeIsRejectedOnTheEnd() {
		assertThat(violations(FROM, FROM)).extracting(FieldViolation::field).containsExactly("to");
		assertThat(violations(FROM, FROM.minusSeconds(1))).extracting(FieldViolation::field).containsExactly("to");
	}

	@Test
	void missingLimitsAreAllReported() {
		assertThat(violations(null, null)).extracting(FieldViolation::field).containsExactly("from", "to");
		assertThat(violations(FROM, null)).extracting(FieldViolation::field).containsExactly("to");
	}

	@Test
	void theMaximumLengthMustBePositive() {
		assertThatThrownBy(() -> ReportRange.of(FROM, FROM.plusSeconds(1), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static java.util.List<FieldViolation> violations(Instant from, Instant to) {
		return catchThrowableOfType(InvalidReportRangeException.class, () -> ReportRange.of(from, to, MAX))
				.violations();
	}

}
