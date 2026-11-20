package dev.triaxis.assistant.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrgencyViewTest {

	@Test
	void everyLevelIsPresentAndTheTotalIsTheSum() {
		UrgencyView view = new UrgencyView(Map.of(TriageLevel.CRITICAL, 2L, TriageLevel.NOT_URGENT, 3L),
				Instant.parse("2026-11-19T12:00:00Z"));

		assertThat(view.active()).containsExactly(Map.entry(TriageLevel.CRITICAL, 2L),
				Map.entry(TriageLevel.URGENT, 0L), Map.entry(TriageLevel.URGENT_2, 0L),
				Map.entry(TriageLevel.NOT_URGENT, 3L), Map.entry(TriageLevel.NOT_URGENT_2, 0L));
		assertThat(view.total()).isEqualTo(5);
	}

	@Test
	void anEmptyViewHasZerosAndNoTime() {
		assertThat(UrgencyView.empty().total()).isZero();
		assertThat(UrgencyView.empty().active()).hasSize(5);
		assertThat(UrgencyView.empty().updatedAt()).isNull();
	}

	@Test
	void aNegativeCountIsImpossible() {
		assertThatThrownBy(() -> new UrgencyView(Map.of(TriageLevel.URGENT, -1L), null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anEventNeedsEveryField() {
		assertThatThrownBy(() -> new UrgencyEvent(UUID.randomUUID(), null, UrgencyEvent.Kind.PRESENT, Instant.now()))
				.isInstanceOf(NullPointerException.class);
	}

}
