package dev.triaxis.assistant.domain.model;

/** How a question ended; also the tag of the metric {@code assistant.answers}. */
public enum AnswerOutcome {
	/** Answered from the corpus, with at least one valid source. */
	ANSWERED,
	/** No excerpt was close enough, or the model said the excerpts do not answer: the fixed notice. */
	NO_PROTOCOL,
	/** The model answer failed the citation checks and was replaced by the fixed notice. */
	REJECTED,
	/** The provider was unavailable (503). */
	DEGRADED
}
