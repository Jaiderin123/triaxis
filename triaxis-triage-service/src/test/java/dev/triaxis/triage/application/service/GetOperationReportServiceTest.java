package dev.triaxis.triage.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.triaxis.triage.domain.exception.InvalidReportRangeException;
import dev.triaxis.triage.domain.model.HourlyLoad;
import dev.triaxis.triage.domain.model.ReportRange;
import dev.triaxis.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetOperationReportServiceTest {

	private static final Instant FROM = Instant.parse("2026-11-19T00:00:00Z");

	private final List<ReportRange> asked = new ArrayList<>();
	private final HourlyLoad load = new HourlyLoad(FROM.plusSeconds(3600), 1, Map.of(TriageLevel.URGENT, 1L), null,
			null, 1, 1);
	private final GetOperationReportService service = new GetOperationReportService(range -> {
		asked.add(range);
		return Flux.just(load);
	}, Duration.ofDays(31));

	@Test
	void theReportHasTheRangeAndTheHoursOfTheQuery() {
		StepVerifier.create(service.report(FROM, FROM.plusSeconds(86_400)))
				.assertNext(report -> {
					assertThat(report.range()).isEqualTo(new ReportRange(FROM, FROM.plusSeconds(86_400)));
					assertThat(report.hours()).containsExactly(load);
				})
				.verifyComplete();
		assertThat(asked).hasSize(1);
	}

	@Test
	void anInvalidRangeNeverReachesTheDatabase() {
		StepVerifier.create(service.report(FROM, FROM)).expectError(InvalidReportRangeException.class).verify();
		StepVerifier.create(service.report(FROM, FROM.plus(Duration.ofDays(32))))
				.expectError(InvalidReportRangeException.class).verify();

		assertThat(asked).isEmpty();
	}

	@Test
	void theMaximumRangeMustBePositive() {
		assertThatThrownBy(() -> new GetOperationReportService(range -> Flux.empty(), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
