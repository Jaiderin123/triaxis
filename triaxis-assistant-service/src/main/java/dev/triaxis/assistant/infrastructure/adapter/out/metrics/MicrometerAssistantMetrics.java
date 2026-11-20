package dev.triaxis.assistant.infrastructure.adapter.out.metrics;

import java.util.Locale;

import dev.triaxis.assistant.application.port.out.AssistantMetrics;
import dev.triaxis.assistant.domain.model.AnswerOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Counter {@code assistant.answers} tagged with the outcome ({@code answered}, {@code no_protocol}, ...). */
@Component
class MicrometerAssistantMetrics implements AssistantMetrics {

	private final MeterRegistry registry;

	MicrometerAssistantMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void answered(AnswerOutcome outcome) {
		registry.counter("assistant.answers", "outcome", outcome.name().toLowerCase(Locale.ROOT)).increment();
	}

}
