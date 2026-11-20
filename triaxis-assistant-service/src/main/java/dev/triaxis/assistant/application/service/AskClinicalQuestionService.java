package dev.triaxis.assistant.application.service;

import java.util.List;

import dev.triaxis.assistant.application.port.in.AskClinicalQuestionUseCase;
import dev.triaxis.assistant.application.port.out.AssistantMetrics;
import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.domain.exception.AssistantUnavailableException;
import dev.triaxis.assistant.domain.model.AnswerOutcome;
import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.service.CitationValidator;
import dev.triaxis.assistant.domain.service.QuestionPolicy;
import dev.triaxis.assistant.domain.service.RetrievalPolicy;
import reactor.core.publisher.Mono;

/**
 * Embeds the question, retrieves the closest sections, and answers only from the ones within the threshold.
 * When none is close enough the model is never called (RN-05). The model answer is accepted only if its
 * citations are valid; the sources always come from the corpus metadata (ADR 0007).
 */
public class AskClinicalQuestionService implements AskClinicalQuestionUseCase {

	private final LlmPort llm;
	private final ProtocolChunkRepository chunks;
	private final CorpusState corpus;
	private final AssistantMetrics metrics;
	private final QuestionPolicy questions;
	private final RetrievalPolicy retrieval;
	private final CitationValidator citations;

	public AskClinicalQuestionService(LlmPort llm, ProtocolChunkRepository chunks, CorpusState corpus,
			AssistantMetrics metrics, QuestionPolicy questions, RetrievalPolicy retrieval,
			CitationValidator citations) {
		this.llm = llm;
		this.chunks = chunks;
		this.corpus = corpus;
		this.metrics = metrics;
		this.questions = questions;
		this.retrieval = retrieval;
		this.citations = citations;
	}

	@Override
	public Mono<ClinicalAnswer> ask(String question) {
		return Mono.fromCallable(() -> questions.validate(question))
				.flatMap(valid -> corpus.isReady() ? answer(valid)
						: Mono.error(new AssistantUnavailableException("The protocol corpus is not ready yet")))
				.doOnNext(answer -> metrics.answered(answer.outcome()))
				.doOnError(AssistantUnavailableException.class, unavailable -> metrics.answered(AnswerOutcome.DEGRADED));
	}

	private Mono<ClinicalAnswer> answer(String question) {
		return llm.embed(question)
				.flatMap(vector -> chunks.findNearest(vector, llm.embeddingModel(), retrieval.topK()).collectList())
				.map(retrieval::relevant)
				.flatMap(relevant -> relevant.isEmpty() ? Mono.just(ClinicalAnswer.noProtocol())
						: generate(question, relevant));
	}

	private Mono<ClinicalAnswer> generate(String question, List<NumberedExcerpt> relevant) {
		return llm.answer(question, relevant).map(answer -> citations.validate(answer, relevant));
	}

}
