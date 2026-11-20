package dev.triaxis.triage.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the operation report (RF-05).
 *
 * @param maxRange  longest range accepted (ASSUMPTIONS A42)
 * @param timeout   longest wait for the report query; longer than the session {@code statement_timeout}, so the
 *                  database ends a slow query before the request gives up on it
 */
@ConfigurationProperties("triage.report")
public record ReportProperties(@DefaultValue("P31D") Duration maxRange, @DefaultValue("PT15S") Duration timeout) {

	public ReportProperties {
		if (maxRange == null || maxRange.isZero() || maxRange.isNegative() || timeout == null || timeout.isZero()
				|| timeout.isNegative()) {
			throw new IllegalArgumentException("triage.report.max-range and triage.report.timeout must be positive");
		}
	}

}
