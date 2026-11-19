package dev.triaxis.triage.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Body of {@code POST /triage/pacientes}. The specification does not name the JSON fields, so the
 * canonical Spanish names accept a few common variants as aliases. Business validation is done by the domain.
 */
public record RegisterPatientRequest(
		@JsonAlias({"nombre", "fullName"}) String nombreCompleto,
		@JsonAlias({"age"}) Integer edad,
		@JsonAlias({"fc", "heartRate"}) Integer frecuenciaCardiaca,
		@JsonAlias({"sistolica", "presionSistolicaMmHg", "systolic"}) Integer presionSistolica,
		@JsonAlias({"diastolica", "presionDiastolicaMmHg", "diastolic"}) Integer presionDiastolica,
		@JsonAlias({"SpO2", "saturacion", "saturacionOxigeno"}) Integer spo2,
		@JsonAlias({"temp", "temperature"}) BigDecimal temperatura,
		@JsonAlias({"pain"}) Integer dolor,
		@JsonAlias({"GCS", "glasgow"}) Integer gcs,
		@JsonAlias({"queja", "chiefComplaint", "motivoConsulta"}) String quejaPrincipal) {
}
