package dev.triaxis.triage.infrastructure.adapter.in.web.mapper;

import java.util.Map;

/** Translates the domain field names used in validation errors to the names of the JSON contract. */
public final class ExternalFieldNames {

	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("fullName", "nombreCompleto"),
			Map.entry("age", "edad"),
			Map.entry("heartRate", "frecuenciaCardiaca"),
			Map.entry("systolic", "presionSistolica"),
			Map.entry("diastolic", "presionDiastolica"),
			Map.entry("spo2", "spo2"),
			Map.entry("temperature", "temperatura"),
			Map.entry("pain", "dolor"),
			Map.entry("gcs", "gcs"),
			Map.entry("chiefComplaint", "quejaPrincipal"));

	private ExternalFieldNames() {
	}

	public static String of(String domainField) {
		return NAMES.getOrDefault(domainField, domainField);
	}

}
