package dev.triaxis.triage.infrastructure.adapter.out.alert;

import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.model.EscalationAlert;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ExternalValues;

/**
 * External form of an alert (ARCHITECTURE section 6.8): the {@code text} that Slack and Teams incoming webhooks
 * show, and the structured fields in Spanish. It has no name, vital signs or complaint (ASSUMPTIONS A44).
 */
record AlertMessage(
		String text,
		String tipo,
		UUID pacienteId,
		String nivel,
		int slaMinutos,
		long minutosEsperando,
		Instant escaladoEn,
		String ruta) {

	static final String TYPE = "paciente_escalado";

	static AlertMessage of(EscalationAlert alert) {
		String level = ExternalValues.level(alert.level());
		String path = "/triage/pacientes/" + alert.patientId();
		String text = "Paciente escalado: nivel %s, %d min de espera (SLA %d min). Ver %s"
				.formatted(level, alert.minutesWaiting(), alert.slaMinutes(), path);
		return new AlertMessage(text, TYPE, alert.patientId(), level, alert.slaMinutes(), alert.minutesWaiting(),
				alert.escalatedAt(), path);
	}

}
