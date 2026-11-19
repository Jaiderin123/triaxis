package dev.triaxis.triage.infrastructure.adapter.out.metrics;

import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.domain.model.TriageLevel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Counter {@code triage.classification} tagged with the assigned level. */
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

}
