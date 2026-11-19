package dev.triaxis.triage.domain.model;

import java.math.BigDecimal;

/** Valid vital signs. Build them through {@link Patient#register}, which validates the raw data first. */
public record VitalSigns(
		int heartRate,
		int systolic,
		int diastolic,
		int spo2,
		BigDecimal temperature,
		int pain,
		int gcs) {
}
