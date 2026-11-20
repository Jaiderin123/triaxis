package dev.triaxis.triage.application.port.out;

import dev.triaxis.triage.domain.model.AlertOutcome;
import dev.triaxis.triage.domain.model.TriageLevel;

public interface TriageMetrics {

	void patientClassified(TriageLevel level);

	void patientEscalated(TriageLevel level);

	void alertHandled(AlertOutcome outcome);

}
