package dev.triaxis.assistant.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.domain.exception.InvalidProtocolException;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.ProtocolDocument;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;
import dev.triaxis.assistant.domain.model.StoredChunk;
import dev.triaxis.assistant.domain.service.ProtocolParser;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class IngestCorpusServiceTest {

	private static final String PROTO_001 = """
			# PROTO-001: Crisis Hipertensiva, manejo inicial
			## Definición
			Presión arterial alta.
			## Medidas iniciales
			Reconfirmar la presión.
			""";

	private static final String PROTO_002 = """
			# PROTO-002: Sepsis
			## Criterios
			Frecuencia respiratoria alta.
			""";

	/** In-memory store and a fake model that counts the embeddings it produces. */
	private static class Fakes implements ProtocolChunkRepository, LlmPort {

		final Map<String, StoredChunk> stored = new LinkedHashMap<>();
		final List<String> embeddedTexts = new ArrayList<>();
		String model = "fake-v1";

		@Override
		public Flux<StoredChunk> findAll() {
			return Flux.defer(() -> Flux.fromIterable(List.copyOf(stored.values())));
		}

		@Override
		public Mono<Void> save(ProtocolChunk chunk, float[] embedding, String embeddingModel, Instant now) {
			return Mono.fromRunnable(() -> stored.put(chunk.id(),
					new StoredChunk(chunk.id(), chunk.contentHash(), embeddingModel)));
		}

		@Override
		public Mono<Long> deleteAllExcept(Set<String> keep) {
			return Mono.fromSupplier(() -> {
				long before = stored.size();
				stored.keySet().retainAll(keep);
				return before - stored.size();
			});
		}

		@Override
		public Flux<RetrievedExcerpt> findNearest(float[] query, String embeddingModel, int limit) {
			return Flux.empty();
		}

		@Override
		public String embeddingModel() {
			return model;
		}

		@Override
		public Mono<dev.triaxis.assistant.domain.model.ModelAnswer> answer(String question,
				List<dev.triaxis.assistant.domain.model.NumberedExcerpt> excerpts) {
			return Mono.error(new UnsupportedOperationException("not used by the ingestion"));
		}

		@Override
		public Mono<float[]> embed(String text) {
			return Mono.fromSupplier(() -> {
				embeddedTexts.add(text);
				return new float[] {1f, 0f};
			});
		}

	}

	private final Fakes fakes = new Fakes();
	private final CorpusState state = new CorpusState();
	private List<ProtocolDocument> files = List.of(new ProtocolDocument("PROTO-001.md", PROTO_001),
			new ProtocolDocument("PROTO-002.md", PROTO_002));

	private IngestCorpusService service() {
		return new IngestCorpusService(() -> Mono.just(files), fakes, fakes,
				() -> Instant.parse("2026-11-19T12:00:00Z"), new ProtocolParser(), state);
	}

	private void ingest() {
		IngestCorpusService service = service();
		service.loadCorpus().flatMap(service::ingest).block();
	}

	@Test
	void theFirstIngestionEmbedsEverySection() {
		IngestCorpusService service = service();

		StepVerifier.create(service.loadCorpus().flatMap(service::ingest))
				.assertNext(report -> {
					assertThat(report.chunks()).isEqualTo(3);
					assertThat(report.embedded()).isEqualTo(3);
					assertThat(report.removed()).isZero();
				})
				.verifyComplete();
		assertThat(fakes.stored).containsOnlyKeys("PROTO-001#definicion", "PROTO-001#medidas-iniciales",
				"PROTO-002#criterios");
		assertThat(fakes.embeddedTexts).contains("Sepsis. Criterios. Frecuencia respiratoria alta.");
		assertThat(state.isReady()).isTrue();
		assertThat(state.lastIngestion()).hasValueSatisfying(report -> assertThat(report.embedded()).isEqualTo(3));
	}

	@Test
	void theCorpusIsNotReadyWhileTheIngestionFails() {
		IngestCorpusService failing = new IngestCorpusService(() -> Mono.just(files), fakes, new Fakes() {
			@Override
			public Mono<float[]> embed(String text) {
				return Mono.error(new IllegalStateException("provider down"));
			}
		}, Instant::now, new ProtocolParser(), state);

		StepVerifier.create(failing.loadCorpus().flatMap(failing::ingest)).expectError().verify();
		assertThat(state.isReady()).isFalse();
	}

	@Test
	void aSecondIngestionEmbedsNothing() {
		ingest();
		fakes.embeddedTexts.clear();
		IngestCorpusService service = service();

		StepVerifier.create(service.loadCorpus().flatMap(service::ingest))
				.assertNext(report -> assertThat(report.embedded()).isZero())
				.verifyComplete();
		assertThat(fakes.embeddedTexts).isEmpty();
	}

	@Test
	void onlyAChangedSectionIsEmbeddedAgain() {
		ingest();
		fakes.embeddedTexts.clear();
		files = List.of(new ProtocolDocument("PROTO-001.md", PROTO_001.replace("Reconfirmar", "Confirmar")),
				new ProtocolDocument("PROTO-002.md", PROTO_002));

		ingest();

		assertThat(fakes.embeddedTexts).singleElement().asString().contains("Confirmar la presión");
	}

	@Test
	void aNewEmbeddingModelEmbedsEverySectionAgain() {
		ingest();
		fakes.embeddedTexts.clear();
		fakes.model = "fake-v2";

		ingest();

		assertThat(fakes.embeddedTexts).hasSize(3);
		assertThat(fakes.stored.values()).allMatch(chunk -> chunk.embeddingModel().equals("fake-v2"));
	}

	@Test
	void sectionsThatLeftTheCorpusAreRemoved() {
		ingest();
		files = List.of(new ProtocolDocument("PROTO-001.md", PROTO_001));
		IngestCorpusService service = service();

		StepVerifier.create(service.loadCorpus().flatMap(service::ingest))
				.assertNext(report -> assertThat(report.removed()).isEqualTo(1))
				.verifyComplete();
		assertThat(fakes.stored).doesNotContainKey("PROTO-002#criterios");
	}

	@Test
	void anEmptyCorpusIsRejected() {
		files = List.of();

		StepVerifier.create(service().loadCorpus()).expectError(InvalidProtocolException.class).verify();
	}

	@Test
	void aMalformedFileIsRejected() {
		files = List.of(new ProtocolDocument("broken.md", "no title"));

		StepVerifier.create(service().loadCorpus()).expectError(InvalidProtocolException.class).verify();
	}

	@Test
	void theSameSectionInTwoFilesIsRejected() {
		files = List.of(new ProtocolDocument("a.md", PROTO_002), new ProtocolDocument("b.md", PROTO_002));

		StepVerifier.create(service().loadCorpus())
				.expectErrorMatches(error -> error instanceof InvalidProtocolException
						&& error.getMessage().contains("PROTO-002#criterios"))
				.verify();
	}

}
