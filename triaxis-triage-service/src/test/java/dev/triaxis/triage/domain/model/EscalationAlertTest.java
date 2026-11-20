package dev.triaxis.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EscalationAlertTest {

	private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
	private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:18:00Z");

	private static PatientEvent event(PatientEventType type, Instant at) {
		return new PatientEvent(EVENT_ID, type, at, PATIENT_ID, TriageLevel.CRITICAL, PatientStatus.REGISTERED,
				PatientStatus.REGISTERED, 5, true);
	}

	@Test
	void carriesTheMinimalContentOfTheEscalation() {
		Instant escalatedAt = REGISTERED_AT.plusSeconds(12 * 60 + 30);

		EscalationAlert alert = EscalationAlert.of(event(PatientEventType.ESCALATED, escalatedAt), REGISTERED_AT);

		assertThat(alert.eventId()).isEqualTo(EVENT_ID);
		assertThat(alert.patientId()).isEqualTo(PATIENT_ID);
		assertThat(alert.level()).isEqualTo(TriageLevel.CRITICAL);
		assertThat(alert.slaMinutes()).isEqualTo(5);
		assertThat(alert.escalatedAt()).isEqualTo(escalatedAt);
		// Whole minutes waited, rounded down, from the registration to the escalation.
		assertThat(alert.minutesWaiting()).isEqualTo(12);
	}

	@Test
	void onlyAnEscalationBecomesAnAlert() {
		assertThatThrownBy(() -> EscalationAlert.of(event(PatientEventType.REGISTERED, REGISTERED_AT), REGISTERED_AT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theEscalationCannotPrecedeTheRegistration() {
		assertThatThrownBy(() -> EscalationAlert.of(event(PatientEventType.ESCALATED, REGISTERED_AT.minusSeconds(1)),
				REGISTERED_AT)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void carriesNoNameVitalSignsOrComplaint() {
		// The alert type has no field for them: the content is the minimum of ASSUMPTIONS A44.
		assertThat(EscalationAlert.class.getRecordComponents())
				.extracting(component -> component.getName())
				.containsExactly("eventId", "patientId", "level", "slaMinutes", "minutesWaiting", "escalatedAt");
	}

}
