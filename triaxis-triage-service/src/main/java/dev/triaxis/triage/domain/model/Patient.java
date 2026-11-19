package dev.triaxis.triage.domain.model;

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

}
