package dev.triaxis.triage.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import dev.triaxis.triage.domain.exception.FieldViolation;
import dev.triaxis.triage.domain.exception.InvalidPatientDataException;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.model.VitalSigns;

/** Validates registration data against the ranges of the requirements and reports every violation. */
public final class RegistrationValidator {

	public static final int MAX_COMPLAINT_LENGTH = 500;

	private static final BigDecimal MIN_TEMPERATURE = new BigDecimal("34.0");
	private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("42.0");

	private RegistrationValidator() {
	}

	public static VitalSigns validate(RegistrationData data) {
		List<FieldViolation> violations = new ArrayList<>();

		if (data.fullName() == null || data.fullName().isBlank()) {
			violations.add(new FieldViolation("fullName", "is required"));
		}
		else if (data.fullName().strip().length() < 3 || data.fullName().strip().length() > 100) {
			violations.add(new FieldViolation("fullName", "must have between 3 and 100 characters"));
		}
		if (data.chiefComplaint() == null || data.chiefComplaint().isBlank()) {
			violations.add(new FieldViolation("chiefComplaint", "is required"));
		}
		else if (data.chiefComplaint().strip().length() > MAX_COMPLAINT_LENGTH) {
			violations.add(new FieldViolation("chiefComplaint",
					"must have at most " + MAX_COMPLAINT_LENGTH + " characters"));
		}
		range(violations, "age", data.age(), 0, 120);
		range(violations, "heartRate", data.heartRate(), 20, 250);
		range(violations, "systolic", data.systolic(), 60, 260);
		range(violations, "diastolic", data.diastolic(), 30, 160);
		range(violations, "spo2", data.spo2(), 70, 100);
		range(violations, "pain", data.pain(), 0, 10);
		range(violations, "gcs", data.gcs(), 3, 15);
		temperature(violations, data.temperature());

		if (data.systolic() != null && data.diastolic() != null && data.diastolic() >= data.systolic()
				&& inRange(data.diastolic(), 30, 160) && inRange(data.systolic(), 60, 260)) {
			violations.add(new FieldViolation("diastolic", "must be lower than the systolic pressure"));
		}

		if (!violations.isEmpty()) {
			throw new InvalidPatientDataException(violations);
		}
		return new VitalSigns(data.heartRate(), data.systolic(), data.diastolic(), data.spo2(),
				data.temperature(), data.pain(), data.gcs());
	}

	private static void range(List<FieldViolation> violations, String field, Integer value, int min, int max) {
		if (value == null) {
			violations.add(new FieldViolation(field, "is required"));
		}
		else if (!inRange(value, min, max)) {
			violations.add(new FieldViolation(field, "must be between " + min + " and " + max));
		}
	}

	private static boolean inRange(Integer value, int min, int max) {
		return value != null && value >= min && value <= max;
	}

	private static void temperature(List<FieldViolation> violations, BigDecimal value) {
		if (value == null) {
			violations.add(new FieldViolation("temperature", "is required"));
		}
		else if (value.compareTo(MIN_TEMPERATURE) < 0 || value.compareTo(MAX_TEMPERATURE) > 0) {
			violations.add(new FieldViolation("temperature", "must be between 34.0 and 42.0"));
		}
		else if (value.stripTrailingZeros().scale() > 1) {
			violations.add(new FieldViolation("temperature", "must have at most one decimal"));
		}
	}

}
