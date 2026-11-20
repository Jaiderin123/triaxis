package dev.triaxis.triage.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.service.RegistrationValidator;
import dev.triaxis.triage.domain.service.SlaPolicy;
import dev.triaxis.triage.domain.service.TriageClassifier;

public record Patient(
		UUID id,
		String fullName,
		int age,
		VitalSigns vitalSigns,
		String chiefComplaint,
		TriageLevel level,
		int slaMinutes,
		PatientStatus status,
		boolean escalated,
		Instant escalatedAt,
		Instant registeredAt,
		Instant statusChangedAt,
		Instant consultationStartedAt) {

	/**
	 * Validates the data, classifies the patient and creates it in the {@code REGISTERED} state.
	 *
	 * @throws dev.triaxis.triage.domain.exception.InvalidPatientDataException listing every invalid field
	 */
	public static Patient register(UUID id, RegistrationData data, TriageClassifier classifier, SlaPolicy sla,
			Instant now) {
		VitalSigns vitals = RegistrationValidator.validate(data);
		String fullName = data.fullName().strip();
		String complaint = data.chiefComplaint().strip();
		TriageLevel level = classifier.classify(vitals, complaint);
		return new Patient(id, fullName, data.age(), vitals, complaint, level, sla.minutesFor(level),
				PatientStatus.REGISTERED, false, null, now, now, null);
	}

	/**
	 * The SLA is exceeded only strictly after the limit: a patient exactly at the limit is not overdue yet
	 * (ASSUMPTIONS A6).
	 */
	public boolean slaExceededAt(Instant now) {
		return registeredAt.plus(Duration.ofMinutes(slaMinutes)).isBefore(now);
	}

	/** Only a waiting patient that was never escalated and exceeded its SLA is escalated (RN-03). */
	public boolean escalationDueAt(Instant now) {
		return status == PatientStatus.REGISTERED && !escalated && slaExceededAt(now);
	}

	/**
	 * Marks the patient as escalated. The status and the time of the last status change do not change:
	 * escalation is an alert, not a state (ASSUMPTIONS A9).
	 *
	 * @throws IllegalStateException when the escalation is not due
	 */
	public Patient escalate(Instant now) {
		if (!escalationDueAt(now)) {
			throw new IllegalStateException("Patient " + id + " is not due for escalation");
		}
		return new Patient(id, fullName, age, vitalSigns, chiefComplaint, level, slaMinutes, status, true, now,
				registeredAt, statusChangedAt, consultationStartedAt);
	}

}
