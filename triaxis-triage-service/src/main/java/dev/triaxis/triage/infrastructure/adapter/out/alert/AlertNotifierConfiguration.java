package dev.triaxis.triage.infrastructure.adapter.out.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.triage.application.port.out.AlertNotifier;
import dev.triaxis.triage.infrastructure.config.AlertProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the alert channel with {@code triage.alerts.channel} (ADR 0011). */
@Configuration
class AlertNotifierConfiguration {

	@Bean
	AlertNotifier alertNotifier(AlertProperties properties, ObjectMapper json) {
		return switch (properties.channel()) {
			case LOG -> new LoggingAlertNotifier();
			case WEBHOOK -> new WebhookAlertNotifier(properties.webhook().uri(),
					properties.webhook().connectTimeout(), properties.webhook().responseTimeout(), json);
		};
	}

}
