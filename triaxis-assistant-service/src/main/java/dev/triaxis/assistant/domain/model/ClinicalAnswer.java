package dev.triaxis.assistant.domain.model;

import java.util.List;

/**
 * The checked answer. Only {@link AnswerOutcome#ANSWERED} carries text and sources; the other outcomes are
 * rendered as the fixed "no protocol available" notice at the boundary, so no clinical text leaks through them.
 */
public record ClinicalAnswer(AnswerOutcome outcome, String text, List<Source> sources) {

	public ClinicalAnswer {
		sources = List.copyOf(sources);
		if (outcome == AnswerOutcome.ANSWERED && (text == null || text.isBlank() || sources.isEmpty())) {
			throw new IllegalArgumentException("An answer needs text and at least one source");
		}
		if (outcome != AnswerOutcome.ANSWERED && (!sources.isEmpty() || (text != null && !text.isEmpty()))) {
			throw new IllegalArgumentException("Only an answered question carries text or sources");
		}
	}

	public static ClinicalAnswer answered(String text, List<Source> sources) {
		return new ClinicalAnswer(AnswerOutcome.ANSWERED, text, sources);
	}

	public static ClinicalAnswer noProtocol() {
		return new ClinicalAnswer(AnswerOutcome.NO_PROTOCOL, "", List.of());
	}

	public static ClinicalAnswer rejected() {
		return new ClinicalAnswer(AnswerOutcome.REJECTED, "", List.of());
	}

}
