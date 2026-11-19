package dev.triaxis.triage.domain.service;

import java.util.Map;

import dev.triaxis.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaPolicyTest {

	@Test
	void requiresAPositiveValueForEveryLevel() {
		assertThatThrownBy(() -> new SlaPolicy(Map.of(TriageLevel.CRITICAL, 5)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SlaPolicy(Map.of(
				TriageLevel.CRITICAL, 0, TriageLevel.URGENT, 10, TriageLevel.URGENT_2, 30,
				TriageLevel.NOT_URGENT, 60, TriageLevel.NOT_URGENT_2, 120)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CRITICAL");
	}

}
