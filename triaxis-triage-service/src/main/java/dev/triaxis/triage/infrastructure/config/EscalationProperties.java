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
 */
@ConfigurationProperties("triage.escalation")
public record EscalationProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("PT30S") Duration interval,
		@DefaultValue("100") int batchSize) {
}
