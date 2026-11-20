package dev.triaxis.assistant.infrastructure.adapter.out.llm.mock;

import java.util.List;

import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import reactor.core.publisher.Mono;

/**
 * The default provider: no key, no network, deterministic. Embeddings are a bag of words, and the answer is
 * extractive (the text of the closest excerpt, cited), so even the mock never writes clinical text of its own.
 */
public class MockLlmAdapter implements LlmPort {

	public static final String EMBEDDING_MODEL = "mock-bag-of-words-v1";

	@Override
	public String embeddingModel() {
		return EMBEDDING_MODEL;
	}

	@Override
	public Mono<float[]> embed(String text) {
		return Mono.fromSupplier(() -> BagOfWordsEmbedding.embed(text));
	}

	@Override
	public Mono<ModelAnswer> answer(String question, List<NumberedExcerpt> excerpts) {
		return Mono.fromSupplier(() -> excerpts.isEmpty() ? ModelAnswer.noProtocolFound()
				: new ModelAnswer(excerpts.get(0).chunk().content(), List.of(excerpts.get(0).number()), false));
	}

}
