package dev.triaxis.triage.infrastructure.adapter.out.alert;

import dev.triaxis.triage.application.port.out.AlertNotifier;
import dev.triaxis.triage.domain.model.EscalationAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default channel (ADR 0011): one structured log line per alert, so the system alerts with no external account.
 * The line carries only the minimal content of the alert (ASSUMPTIONS A44), as key-value pairs of the JSON log.
 */
class LoggingAlertNotifier implements AlertNotifier {

	static final String LOGGER = "dev.triaxis.triage.alerts";

	private static final Logger log = LoggerFactory.getLogger(LOGGER);

	@Override
	public Mono<Void> send(EscalationAlert alert) {
		return Mono.fromRunnable(() -> {
			AlertMessage message = AlertMessage.of(alert);
			log.atWarn()
					.addKeyValue("alert.tipo", message.tipo())
					.addKeyValue("alert.pacienteId", message.pacienteId())
					.addKeyValue("alert.nivel", message.nivel())
					.addKeyValue("alert.slaMinutos", message.slaMinutos())
					.addKeyValue("alert.minutosEsperando", message.minutosEsperando())
					.addKeyValue("alert.escaladoEn", message.escaladoEn())
					.addKeyValue("alert.ruta", message.ruta())
					.log(message.text());
		});
	}

}
