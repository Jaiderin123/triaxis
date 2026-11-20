package dev.triaxis.assistant.application.port.in;

import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import reactor.core.publisher.Mono;

/** RF-04 A: answers a question from the clinical protocols, with validated sources (RN-05). */
public interface AskClinicalQuestionUseCase {

	/**
	 * Fails with {@code InvalidQuestionException} for a blank or too long question, and with
	 * {@code AssistantUnavailableException} when the provider is unavailable or the corpus is not ready.
	 */
	Mono<ClinicalAnswer> ask(String question);

}
