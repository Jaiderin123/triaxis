package dev.triaxis.triage.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the SLA escalation loop (RF-03).
 *
 * @param enabled    runs the periodic loop; tests switch it off and call the use case
 * @param interval   time between two cycles
 * @param batchSize  patients escalated per statement; a full batch starts another one at once
 * @param batchTimeout  longest time of one batch (about three statements per patient); a stuck batch is
 *                      rolled back and the loop goes on at the next tick
 */
@ConfigurationProperties("triage.escalation")
public record EscalationProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("PT30S") Duration interval,
		@DefaultValue("100") int batchSize,
		@DefaultValue("PT60S") Duration batchTimeout) {

	public EscalationProperties {
		if (batchSize < 1) {
			throw new IllegalArgumentException("triage.escalation.batch-size must be at least 1");
		}
		if (interval == null || interval.isZero() || interval.isNegative() || batchTimeout == null
				|| batchTimeout.isZero() || batchTimeout.isNegative()) {
			throw new IllegalArgumentException("triage.escalation.interval and batch-timeout must be positive");
		}
	}

}
