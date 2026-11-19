package dev.triaxis.triage.infrastructure.adapter.in.web.mapper;

import java.util.Map;

import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;

/** Spanish values of the external contract, kept out of the domain enums. */
public final class ExternalValues {

	private static final Map<TriageLevel, String> LEVELS = Map.of(
			TriageLevel.CRITICAL, "CRITICO",
			TriageLevel.URGENT, "URGENTE",
			TriageLevel.URGENT_2, "URGENTE_2",
			TriageLevel.NOT_URGENT, "NO_URGENTE",
			TriageLevel.NOT_URGENT_2, "NO_URGENTE_2");

	private static final Map<PatientStatus, String> STATUSES = Map.of(
			PatientStatus.REGISTERED, "REGISTRADO",
			PatientStatus.IN_CONSULTATION, "EN_CONSULTA",
			PatientStatus.DISCHARGED, "ALTA");

	private ExternalValues() {
	}

	public static String level(TriageLevel level) {
		return LEVELS.get(level);
	}

	public static String status(PatientStatus status) {
		return STATUSES.get(status);
	}

	/** Returns the status for an external value, or null when the value is unknown. */
	public static PatientStatus parseStatus(String external) {
		return STATUSES.entrySet().stream()
				.filter(entry -> entry.getValue().equals(external))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);
	}

	public static String allowedStatuses() {
		return String.join(", ", STATUSES.get(PatientStatus.REGISTERED), STATUSES.get(PatientStatus.IN_CONSULTATION),
				STATUSES.get(PatientStatus.DISCHARGED));
	}

}
