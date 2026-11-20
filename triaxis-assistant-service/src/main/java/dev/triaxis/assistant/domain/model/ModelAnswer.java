package dev.triaxis.assistant.domain.model;

import java.util.List;

/**
 * What the model returned, before any check.
 *
 * @param text        the answer text; empty when the model found no protocol or the output was unusable
 * @param citations   numbers of the excerpts the model says it used
 * @param noProtocol  the model said the excerpts do not answer the question
 */
public record ModelAnswer(String text, List<Integer> citations, boolean noProtocol) {

	public ModelAnswer {
		text = text == null ? "" : text.strip();
		citations = citations == null ? List.of() : List.copyOf(citations);
	}

	public static ModelAnswer noProtocolFound() {
		return new ModelAnswer("", List.of(), true);
	}

	/** The output could not be read (not the expected JSON); it is treated like an invalid citation. */
	public static ModelAnswer unusable() {
		return new ModelAnswer("", List.of(), false);
	}

}
