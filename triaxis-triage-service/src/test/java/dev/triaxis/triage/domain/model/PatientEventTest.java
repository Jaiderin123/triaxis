package dev.triaxis.triage.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientEventTest {

	private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
	private static final Instant AT = Instant.parse("2026-11-18T15:30:00Z");

	private static Patient patient(PatientStatus status) {
		return new Patient(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Ana Gomez", 54,
				new VitalSigns(130, 180, 110, 97, new BigDecimal("36.8"), 8, 15), "Dolor toracico",
				TriageLevel.CRITICAL, 5, status, false, null, AT, AT, null);
	}

	@Test
	void registeredEventCarriesTheLevelAndHasNoPreviousStatus() {
		PatientEvent event = PatientEvent.registered(EVENT_ID, patient(PatientStatus.REGISTERED));

		assertThat(event.type()).isEqualTo(PatientEventType.REGISTERED);
		assertThat(event.eventId()).isEqualTo(EVENT_ID);
		assertThat(event.level()).isEqualTo(TriageLevel.CRITICAL);
		assertThat(event.previousStatus()).isNull();
		assertThat(event.newStatus()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(event.slaMinutes()).isEqualTo(5);
		assertThat(event.occurredAt()).isEqualTo(AT);
	}

	@Test
	void eventTypeFollowsTheNewStatus() {
		PatientEvent consultation = PatientEvent.statusChanged(EVENT_ID, patient(PatientStatus.IN_CONSULTATION),
				PatientStatus.REGISTERED, AT);
		PatientEvent discharge = PatientEvent.statusChanged(EVENT_ID, patient(PatientStatus.DISCHARGED),
				PatientStatus.IN_CONSULTATION, AT);

		assertThat(consultation.type()).isEqualTo(PatientEventType.CONSULTATION_STARTED);
		assertThat(consultation.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(discharge.type()).isEqualTo(PatientEventType.DISCHARGED);
		assertThat(discharge.previousStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
	}

	@Test
	void nothingGoesBackToRegistered() {
		assertThatThrownBy(() -> PatientEvent.statusChanged(EVENT_ID, patient(PatientStatus.REGISTERED),
				PatientStatus.IN_CONSULTATION, AT)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void escalatedEventKeepsTheStatusAndCarriesTheFlag() {
		Instant escalatedAt = AT.plusSeconds(600);
		Patient escalated = patient(PatientStatus.REGISTERED).escalate(escalatedAt);

		PatientEvent event = PatientEvent.escalated(EVENT_ID, escalated);

		assertThat(event.type()).isEqualTo(PatientEventType.ESCALATED);
		assertThat(event.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(event.newStatus()).isEqualTo(PatientStatus.REGISTERED);
		assertThat(event.escalated()).isTrue();
		assertThat(event.level()).isEqualTo(TriageLevel.CRITICAL);
		assertThat(event.slaMinutes()).isEqualTo(5);
		assertThat(event.occurredAt()).isEqualTo(escalatedAt);
	}

	@Test
	void aPatientThatWasNotEscalatedHasNoEscalationEvent() {
		assertThatThrownBy(() -> PatientEvent.escalated(EVENT_ID, patient(PatientStatus.REGISTERED)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
