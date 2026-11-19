package dev.triaxis.triage.infrastructure.config;

import java.util.Map;
import java.util.Set;

import dev.triaxis.triage.domain.model.TriageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business settings: the SLA per level and the administrative complaints of level 5.
 */
@ConfigurationProperties("triage")
public record TriageProperties(Sla sla, Set<String> administrativeComplaints) {

	public record Sla(Map<TriageLevel, Integer> minutes) {
	}

}
