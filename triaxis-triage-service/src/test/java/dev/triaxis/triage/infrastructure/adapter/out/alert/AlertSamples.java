package dev.triaxis.triage.infrastructure.adapter.out.alert;

import java.time.Instant;
import java.util.UUID;

import dev.triaxis.triage.domain.model.EscalationAlert;
import dev.triaxis.triage.domain.model.TriageLevel;

final class AlertSamples {

	static final UUID PATIENT_ID = UUID.fromString("3a1b0000-0000-0000-0000-000000000001");
	static final Instant ESCALATED_AT = Instant.parse("2026-11-20T15:30:00Z");

	private AlertSamples() {
	}

	static EscalationAlert alert() {
		return new EscalationAlert(UUID.fromString("00000000-0000-0000-0000-0000000000e1"), PATIENT_ID,
				TriageLevel.CRITICAL, 5, 12, ESCALATED_AT);
	}

}
