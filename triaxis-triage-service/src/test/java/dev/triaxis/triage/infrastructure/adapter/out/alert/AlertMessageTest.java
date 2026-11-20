package dev.triaxis.triage.infrastructure.adapter.out.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMessageTest {

	@Test
	void hasTheTextAndTheStructuredFieldsInSpanish() {
		AlertMessage message = AlertMessage.of(AlertSamples.alert());

		assertThat(message.text()).isEqualTo("Paciente escalado: nivel CRITICO, 12 min de espera (SLA 5 min). "
				+ "Ver /triage/pacientes/" + AlertSamples.PATIENT_ID);
		assertThat(message.tipo()).isEqualTo("paciente_escalado");
		assertThat(message.pacienteId()).isEqualTo(AlertSamples.PATIENT_ID);
		assertThat(message.nivel()).isEqualTo("CRITICO");
		assertThat(message.slaMinutos()).isEqualTo(5);
		assertThat(message.minutosEsperando()).isEqualTo(12);
		assertThat(message.escaladoEn()).isEqualTo(AlertSamples.ESCALATED_AT);
		assertThat(message.ruta()).isEqualTo("/triage/pacientes/" + AlertSamples.PATIENT_ID);
	}

}
