package dev.triaxis.triage.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientEscalationTest {

	private static final Instant REGISTERED_AT = Instant.parse("2026-11-18T15:00:00Z");
	/** A level 1 patient has an SLA of 5 minutes. */
	private static final Instant LIMIT = REGISTERED_AT.plusSeconds(5 * 60);

	private static Patient patient(PatientStatus status, boolean escalated) {
		Instant changedAt = status == PatientStatus.REGISTERED ? REGISTERED_AT : REGISTERED_AT.plusSeconds(60);
		return new Patient(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Ana Gomez", 54,
				new VitalSigns(130, 180, 110, 97, new BigDecimal("36.8"), 8, 15), "Dolor toracico",
				TriageLevel.CRITICAL, 5, status, escalated, escalated ? LIMIT : null, REGISTERED_AT, changedAt,
				status == PatientStatus.IN_CONSULTATION ? changedAt : null);
	}

	@Test
	void theSlaIsNotExceededBeforeOrExactlyAtTheLimit() {
		Patient patient = patient(PatientStatus.REGISTERED, false);

		assertThat(patient.slaExceededAt(REGISTERED_AT)).isFalse();
		assertThat(patient.slaExceededAt(LIMIT.minusNanos(1000))).isFalse();
		assertThat(patient.slaExceededAt(LIMIT)).isFalse();
	}

	@Test
	void theSlaIsExceededOneInstantAfterTheLimit() {
		Patient patient = patient(PatientStatus.REGISTERED, false);

		assertThat(patient.slaExceededAt(LIMIT.plusNanos(1000))).isTrue();
		assertThat(patient.escalationDueAt(LIMIT.plusNanos(1000))).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = PatientStatus.class, names = {"IN_CONSULTATION", "DISCHARGED"})
	void aPatientInConsultationOrDischargedIsNeverDue(PatientStatus status) {
		Patient patient = patient(status, false);

		assertThat(patient.escalationDueAt(LIMIT.plusSeconds(3600))).isFalse();
		assertThatThrownBy(() -> patient.escalate(LIMIT.plusSeconds(3600)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anEscalatedPatientIsNotDueAgain() {
		Patient patient = patient(PatientStatus.REGISTERED, true);

		assertThat(patient.escalationDueAt(LIMIT.plusSeconds(3600))).isFalse();
		assertThatThrownBy(() -> patient.escalate(LIMIT.plusSeconds(3600)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aPatientWithinItsSlaCannotBeEscalated() {
		assertThatThrownBy(() -> patient(PatientStatus.REGISTERED, false).escalate(LIMIT))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void escalationSetsTheFlagAndKeepsTheStatusAndItsTime() {
		Patient before = patient(PatientStatus.REGISTERED, false);
		Instant now = LIMIT.plusSeconds(30);

		Patient after = before.escalate(now);

		assertThat(after.escalated()).isTrue();
		assertThat(after.escalatedAt()).isEqualTo(now);
		assertThat(after.status()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(after.statusChangedAt()).isEqualTo(before.statusChangedAt());
		assertThat(after.registeredAt()).isEqualTo(before.registeredAt());
		assertThat(after.level()).isEqualTo(before.level());
	}

}
