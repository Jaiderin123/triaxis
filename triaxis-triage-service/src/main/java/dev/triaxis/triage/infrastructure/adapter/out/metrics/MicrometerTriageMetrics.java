package dev.triaxis.triage.infrastructure.adapter.out.metrics;

import java.util.Locale;

import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.domain.model.AlertOutcome;
import dev.triaxis.triage.domain.model.TriageLevel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Counters {@code triage.classification} and {@code triage.escalations}, tagged with the patient level, and
 * {@code triage.alerts}, tagged with the outcome of the alert.
 */
@Component
class MicrometerTriageMetrics implements TriageMetrics {

	private final MeterRegistry registry;

	MicrometerTriageMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void patientClassified(TriageLevel level) {
		registry.counter("triage.classification", "level", level.name()).increment();
	}

	@Override
	public void patientEscalated(TriageLevel level) {
		registry.counter("triage.escalations", "level", level.name()).increment();
	}

	@Override
	public void alertHandled(AlertOutcome outcome) {
		registry.counter("triage.alerts", "outcome", outcome.name().toLowerCase(Locale.ROOT)).increment();
	}

}
