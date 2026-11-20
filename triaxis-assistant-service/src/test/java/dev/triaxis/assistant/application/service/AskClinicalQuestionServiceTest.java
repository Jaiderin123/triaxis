package dev.triaxis.assistant.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import dev.triaxis.assistant.application.port.out.AssistantMetrics;
import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.domain.exception.AssistantUnavailableException;
import dev.triaxis.assistant.domain.exception.InvalidQuestionException;
import dev.triaxis.assistant.domain.model.AnswerOutcome;
import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.IngestionReport;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;
import dev.triaxis.assistant.domain.model.Source;
import dev.triaxis.assistant.domain.model.StoredChunk;
import dev.triaxis.assistant.domain.service.CitationValidator;
import dev.triaxis.assistant.domain.service.QuestionPolicy;
import dev.triaxis.assistant.domain.service.RetrievalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class AskClinicalQuestionServiceTest {

	private static final ProtocolChunk MEASURES = ProtocolChunk.of("PROTO-001#medidas-iniciales", "PROTO-001",
			"Crisis Hipertensiva", "Medidas iniciales", "Reconfirmar la presión.");
	private static final ProtocolChunk SEPSIS = ProtocolChunk.of("PROTO-002#acciones-iniciales", "PROTO-002",
			"Sepsis", "Acciones iniciales", "Antibiótico en la primera hora.");

	/** A scripted model and store: retrieval returns {@code retrieved}, the model answers with {@code reply}. */
	private static final class Fakes implements LlmPort, ProtocolChunkRepository, AssistantMetrics {

		List<RetrievedExcerpt> retrieved = List.of();
		Function<List<NumberedExcerpt>, Mono<ModelAnswer>> reply = sent -> Mono.just(ModelAnswer.unusable());
		Mono<float[]> embedding = Mono.just(new float[] {1f});
		RuntimeException searchFailure;
		final List<List<NumberedExcerpt>> modelCalls = new ArrayList<>();
		final List<AnswerOutcome> outcomes = new ArrayList<>();
		String lastSearchModel;

		@Override
		public String embeddingModel() {
			return "fake-model";
		}

		@Override
		public Mono<float[]> embed(String text) {
			return embedding;
		}

		@Override
		public Mono<ModelAnswer> answer(String question, List<NumberedExcerpt> excerpts) {
			return Mono.defer(() -> {
				modelCalls.add(excerpts);
				return reply.apply(excerpts);
			});
		}

		@Override
		public Flux<RetrievedExcerpt> findNearest(float[] query, String embeddingModel, int limit) {
			lastSearchModel = embeddingModel;
			return searchFailure != null ? Flux.error(searchFailure) : Flux.fromIterable(retrieved).take(limit);
		}

		@Override
		public Flux<StoredChunk> findAll() {
			return Flux.empty();
		}

		@Override
		public Mono<Void> save(ProtocolChunk chunk, float[] embedding, String embeddingModel, Instant now) {
			return Mono.empty();
		}

		@Override
		public Mono<Long> deleteAllExcept(Set<String> keep) {
			return Mono.just(0L);
		}

		@Override
		public void answered(AnswerOutcome outcome) {
			outcomes.add(outcome);
		}

	}

	private final Fakes fakes = new Fakes();
	private final CorpusState corpus = new CorpusState();
	private final AskClinicalQuestionService service = new AskClinicalQuestionService(fakes, fakes, corpus, fakes,
			new QuestionPolicy(1000), new RetrievalPolicy(4, 0.70), new CitationValidator());

	@BeforeEach
	void theCorpusIsReady() {
		corpus.ready(new IngestionReport(7, 7, 0));
	}

	@Test
	void answersFromTheRetrievedExcerptsWithSourcesFromTheCorpus() {
		fakes.retrieved = List.of(new RetrievedExcerpt(MEASURES, 0.47), new RetrievedExcerpt(SEPSIS, 0.90));
		fakes.reply = sent -> Mono.just(new ModelAnswer("Reconfirmar la presión.", List.of(1), false));

		StepVerifier.create(service.ask("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?"))
				.expectNext(ClinicalAnswer.answered("Reconfirmar la presión.",
						List.of(new Source("PROTO-001", "Medidas iniciales"))))
				.verifyComplete();
		assertThat(fakes.modelCalls).singleElement().isEqualTo(List.of(new NumberedExcerpt(1, MEASURES)));
		assertThat(fakes.lastSearchModel).isEqualTo("fake-model");
		assertThat(fakes.outcomes).containsExactly(AnswerOutcome.ANSWERED);
	}

	@Test
	void withoutARelevantExcerptTheModelIsNeverCalled() {
		fakes.retrieved = List.of(new RetrievedExcerpt(SEPSIS, 0.82));

		StepVerifier.create(service.ask("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?"))
				.expectNext(ClinicalAnswer.noProtocol())
				.verifyComplete();
		assertThat(fakes.modelCalls).isEmpty();
		assertThat(fakes.outcomes).containsExactly(AnswerOutcome.NO_PROTOCOL);
	}

	@Test
	void anEmptyCorpusMatchGivesNoProtocolWithoutTheModel() {
		StepVerifier.create(service.ask("¿Algo?")).expectNext(ClinicalAnswer.noProtocol()).verifyComplete();
		assertThat(fakes.modelCalls).isEmpty();
	}

	@Test
	void aCitationOfAnExcerptThatWasNotSentIsRejected() {
		fakes.retrieved = List.of(new RetrievedExcerpt(MEASURES, 0.47));
		fakes.reply = sent -> Mono.just(new ModelAnswer("Bajar la presión rápido.", List.of(2), false));

		StepVerifier.create(service.ask("¿Medidas?")).expectNext(ClinicalAnswer.rejected()).verifyComplete();
		assertThat(fakes.outcomes).containsExactly(AnswerOutcome.REJECTED);
	}

	@Test
	void theModelSayingNoProtocolGivesTheNotice() {
		fakes.retrieved = List.of(new RetrievedExcerpt(MEASURES, 0.47));
		fakes.reply = sent -> Mono.just(ModelAnswer.noProtocolFound());

		StepVerifier.create(service.ask("¿Medidas?")).expectNext(ClinicalAnswer.noProtocol()).verifyComplete();
	}

	@Test
	void aBlankQuestionIsRejectedBeforeAnyCall() {
		StepVerifier.create(service.ask("   ")).expectError(InvalidQuestionException.class).verify();
		assertThat(fakes.modelCalls).isEmpty();
		assertThat(fakes.outcomes).isEmpty();
	}

	@Test
	void beforeTheCorpusIsReadyTheServiceIsUnavailable() {
		CorpusState notReady = new CorpusState();
		AskClinicalQuestionService early = new AskClinicalQuestionService(fakes, fakes, notReady, fakes,
				new QuestionPolicy(1000), new RetrievalPolicy(4, 0.70), new CitationValidator());

		StepVerifier.create(early.ask("¿Medidas?")).expectError(AssistantUnavailableException.class).verify();
		assertThat(fakes.outcomes).containsExactly(AnswerOutcome.DEGRADED);
	}

	@Test
	void aFailingProtocolStoreIsUnavailableNotAServerError() {
		fakes.searchFailure = new IllegalStateException("connection refused");

		StepVerifier.create(service.ask("¿Medidas?"))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AssistantUnavailableException.class)
						.hasMessageContaining("protocol store"))
				.verify();
		assertThat(fakes.outcomes).containsExactly(AnswerOutcome.DEGRADED);
		assertThat(fakes.modelCalls).isEmpty();
	}

	@Test
	void anUnavailableProviderIsReportedAndCountedAsDegraded() {
		fakes.embedding = Mono.error(new AssistantUnavailableException("provider down"));

		StepVerifier.create(service.ask("¿Medidas?")).expectError(AssistantUnavailableException.class).verify();
		assertThat(fakes.outcomes).containsExactly(AnswerOutcome.DEGRADED);
	}

}
