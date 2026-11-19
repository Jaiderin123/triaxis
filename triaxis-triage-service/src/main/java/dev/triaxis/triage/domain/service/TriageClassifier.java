package dev.triaxis.triage.domain.service;

import java.util.Comparator;
import java.util.List;

import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;
import dev.triaxis.triage.domain.rule.CriticalRule;
import dev.triaxis.triage.domain.rule.TriageLevelRule;
import dev.triaxis.triage.domain.rule.Urgent2Rule;
import dev.triaxis.triage.domain.rule.UrgentRule;

/**
 * Pure classification: rules are evaluated from the most to the least severe level and the first
 * match wins. Without a match the level is NOT_URGENT, or NOT_URGENT_2 for an administrative complaint.
 */
public class TriageClassifier {

	private final List<TriageLevelRule> rules;
	private final ComplaintPolicy complaints;

	public TriageClassifier(List<TriageLevelRule> rules, ComplaintPolicy complaints) {
		this.rules = rules.stream().sorted(Comparator.comparing(TriageLevelRule::level)).toList();
		this.complaints = complaints;
	}

	public static TriageClassifier withDefaultRules(ComplaintPolicy complaints) {
		return new TriageClassifier(List.of(new CriticalRule(), new UrgentRule(), new Urgent2Rule()), complaints);
	}

	public TriageLevel classify(VitalSigns vitals, String chiefComplaint) {
		for (TriageLevelRule rule : rules) {
			if (rule.applies(vitals)) {
				return rule.level();
			}
		}
		return complaints.isAdministrative(chiefComplaint) ? TriageLevel.NOT_URGENT_2 : TriageLevel.NOT_URGENT;
	}

}
