package dev.triaxis.triage.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PatientResponse(
		UUID id,
		String nombreCompleto,
		int edad,
		int frecuenciaCardiaca,
		int presionSistolica,
		int presionDiastolica,
		int spo2,
		BigDecimal temperatura,
		int dolor,
		int gcs,
		String quejaPrincipal,
		String nivel,
		int slaMinutos,
		String estado,
		boolean escalado,
		Instant fechaRegistro,
		Instant fechaCambioEstado,
		long tiempoEnEstadoSegundos) {
}
