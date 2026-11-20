package dev.triaxis.assistant.domain.service;

import dev.triaxis.assistant.domain.exception.InvalidQuestionException;

/** A question is required, not blank and at most {@code maxLength} characters (ASSUMPTIONS A34). */
public class QuestionPolicy {

	private final int maxLength;

	public QuestionPolicy(int maxLength) {
		if (maxLength < 1) {
			throw new IllegalArgumentException("The maximum question length must be positive");
		}
		this.maxLength = maxLength;
	}

	/** Returns the question without surrounding blanks. */
	public String validate(String question) {
		if (question == null || question.isBlank()) {
			throw new InvalidQuestionException("The question is required");
		}
		String trimmed = question.strip();
		if (trimmed.length() > maxLength) {
			throw new InvalidQuestionException("The question must have at most " + maxLength + " characters");
		}
		return trimmed;
	}

}
