package dev.triaxis.triage.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HourlyLoadTest {

	private static final Instant HOUR = Instant.parse("2026-11-19T15:00:00Z");

	@Test
	void everyLevelIsPresentWithZerosAndTheWaitsMayBeUnknown() {
		HourlyLoad load = new HourlyLoad(HOUR, 3, Map.of(TriageLevel.CRITICAL, 1L, TriageLevel.NOT_URGENT, 2L), null,
				null, 3, 1);

		assertThat(load.byLevel()).containsExactly(Map.entry(TriageLevel.CRITICAL, 1L),
				Map.entry(TriageLevel.URGENT, 0L), Map.entry(TriageLevel.URGENT_2, 0L),
				Map.entry(TriageLevel.NOT_URGENT, 2L), Map.entry(TriageLevel.NOT_URGENT_2, 0L));
		assertThat(load.averageWaitMinutes()).isNull();
		assertThat(load.p95WaitMinutes()).isNull();
	}

	@Test
	void theLevelsAddUpToTheTotal() {
		assertThatThrownBy(() -> new HourlyLoad(HOUR, 5, Map.of(TriageLevel.CRITICAL, 1L), null, null, 5, 1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theHourIsAWholeHour() {
		assertThatThrownBy(() -> new HourlyLoad(HOUR.plusSeconds(60), 0, Map.of(), null, null, 0, 1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void waitsAreNeverNegativeAndTheRankStartsAtOne() {
		assertThatThrownBy(() -> new HourlyLoad(HOUR, 0, Map.of(), new BigDecimal("-1"), null, 0, 1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new HourlyLoad(HOUR, 0, Map.of(), null, null, 0, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
