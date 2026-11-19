package dev.triaxis.triage.domain.rule;

import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;

/** One classification rule per level (Strategy). */
public interface TriageLevelRule {

	TriageLevel level();

	boolean applies(VitalSigns vitals);

}
