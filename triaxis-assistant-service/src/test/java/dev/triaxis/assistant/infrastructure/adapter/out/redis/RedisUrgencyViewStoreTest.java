package dev.triaxis.assistant.infrastructure.adapter.out.redis;

import java.time.Instant;
import java.util.UUID;

import dev.triaxis.assistant.AssistantIntegrationTest;
import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.assistant.domain.model.UrgencyEvent.Kind;
import dev.triaxis.assistant.domain.model.UrgencyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** The rules of ADR 0021 applied by the Lua script against a real Redis. */
@AssistantIntegrationTest
class RedisUrgencyViewStoreTest {

	private static final Instant T0 = Instant.parse("2026-11-19T12:00:00Z");

	@Autowired
	RedisUrgencyViewStore store;

	@Autowired
	ReactiveStringRedisTemplate redis;

	@BeforeEach
	void emptyView() {
		store.reset().block();
	}

	private static UrgencyEvent event(UUID patient, TriageLevel level, Kind kind, int seconds) {
		return new UrgencyEvent(patient, level, kind, T0.plusSeconds(seconds));
	}

	private boolean apply(UrgencyEvent event) {
		return store.apply(event).block();
	}

	private long active(TriageLevel level) {
		return store.read().block().active().get(level);
	}

	@Test
	void aRegistrationCountsThePatientOnceEvenIfRepeated() {
		UUID ana = UUID.randomUUID();

		assertThat(apply(event(ana, TriageLevel.CRITICAL, Kind.PRESENT, 0))).isTrue();
		assertThat(apply(event(ana, TriageLevel.CRITICAL, Kind.PRESENT, 0))).isFalse();
		assertThat(apply(event(ana, TriageLevel.CRITICAL, Kind.PRESENT, 30))).isFalse();

		assertThat(active(TriageLevel.CRITICAL)).isEqualTo(1);
	}

	@Test
	void aPatientWhoseRegistrationWasMissedIsCountedByAnyOtherEvent() {
		apply(event(UUID.randomUUID(), TriageLevel.URGENT, Kind.PRESENT, 0));

		assertThat(store.read().block().total()).isEqualTo(1);
		assertThat(active(TriageLevel.URGENT)).isEqualTo(1);
	}

	@Test
	void aDischargeSubtractsOnceAndIsFinal() {
		UUID luis = UUID.randomUUID();
		apply(event(luis, TriageLevel.NOT_URGENT, Kind.PRESENT, 0));

		assertThat(apply(event(luis, TriageLevel.NOT_URGENT, Kind.DISCHARGED, 60))).isTrue();
		assertThat(apply(event(luis, TriageLevel.NOT_URGENT, Kind.DISCHARGED, 60))).isFalse();
		assertThat(apply(event(luis, TriageLevel.NOT_URGENT, Kind.PRESENT, 0))).isFalse();

		assertThat(active(TriageLevel.NOT_URGENT)).isZero();
	}

	@Test
	void aDischargeBeforeTheRegistrationNeverCountsThePatient() {
		UUID late = UUID.randomUUID();

		assertThat(apply(event(late, TriageLevel.URGENT_2, Kind.DISCHARGED, 60))).isFalse();
		assertThat(apply(event(late, TriageLevel.URGENT_2, Kind.PRESENT, 0))).isFalse();

		assertThat(active(TriageLevel.URGENT_2)).isZero();
		assertThat(redis.getExpire(RedisUrgencyViewStore.PATIENT_PREFIX + late).block()).isPositive();
	}

	@Test
	void countsAreKeptPerLevelAndTheNewestEventTimeIsShown() {
		apply(event(UUID.randomUUID(), TriageLevel.CRITICAL, Kind.PRESENT, 10));
		apply(event(UUID.randomUUID(), TriageLevel.CRITICAL, Kind.PRESENT, 50));
		apply(event(UUID.randomUUID(), TriageLevel.NOT_URGENT_2, Kind.PRESENT, 20));

		UrgencyView view = store.read().block();
		assertThat(view.active()).containsEntry(TriageLevel.CRITICAL, 2L).containsEntry(TriageLevel.NOT_URGENT_2, 1L)
				.containsEntry(TriageLevel.URGENT, 0L);
		assertThat(view.total()).isEqualTo(3);
		assertThat(view.updatedAt()).isEqualTo(T0.plusSeconds(50));
	}

	@Test
	void aResetEmptiesTheViewAndMarksItInitialized() {
		apply(event(UUID.randomUUID(), TriageLevel.CRITICAL, Kind.PRESENT, 0));
		redis.delete(RedisUrgencyViewStore.META).block();
		assertThat(store.isInitialized().block()).isFalse();

		store.reset().block();

		assertThat(store.read().block()).isEqualTo(UrgencyView.empty());
		assertThat(store.isInitialized().block()).isTrue();
	}

}
