package dev.triaxis.triage.domain.model;

import java.math.BigDecimal;

/** Raw, unvalidated data to register a patient. Fields are nullable so missing values can be reported. */
public record RegistrationData(
		String fullName,
		Integer age,
		Integer heartRate,
		Integer systolic,
		Integer diastolic,
		Integer spo2,
		BigDecimal temperature,
		Integer pain,
		Integer gcs,
		String chiefComplaint) {
}
