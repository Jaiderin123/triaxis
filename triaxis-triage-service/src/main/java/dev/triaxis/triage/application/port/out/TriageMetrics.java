package dev.triaxis.triage.application.port.out;

import dev.triaxis.triage.domain.model.TriageLevel;

public interface TriageMetrics {

	void patientClassified(TriageLevel level);

}
