package dev.triaxis.triage.infrastructure.adapter.out.metrics;

import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.domain.model.TriageLevel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Counters {@code triage.classification} and {@code triage.escalations}, tagged with the patient level. */
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

}
