package dev.triaxis.assistant.application.port.out;

import java.util.List;

import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import reactor.core.publisher.Mono;

/**
 * The language model provider ({@code mock} or Gemini). Implementations never block the calling thread, and a
 * failure to reach the provider is an error signal (the resilience decorator turns it into a 503).
 */
public interface LlmPort {

	/** Name of the embedding model, stored with each chunk so a model change triggers a new embedding. */
	String embeddingModel();

	Mono<float[]> embed(String text);

	/**
	 * Answers the question using only the given excerpts and reports which excerpt numbers it used, or that
	 * none of them answers the question. An output that cannot be read is {@link ModelAnswer#unusable()}.
	 */
	Mono<ModelAnswer> answer(String question, List<NumberedExcerpt> excerpts);

}
