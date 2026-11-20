package dev.triaxis.assistant.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.triaxis.assistant.application.port.in.IngestCorpusUseCase;
import dev.triaxis.assistant.application.port.out.ClockPort;
import dev.triaxis.assistant.application.port.out.LlmPort;
import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.application.port.out.ProtocolSource;
import dev.triaxis.assistant.domain.exception.InvalidProtocolException;
import dev.triaxis.assistant.domain.model.IngestionReport;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.StoredChunk;
import dev.triaxis.assistant.domain.service.ProtocolParser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class IngestCorpusService implements IngestCorpusUseCase {

	private final ProtocolSource source;
	private final ProtocolChunkRepository chunks;
	private final LlmPort llm;
	private final ClockPort clock;
	private final ProtocolParser parser;
	private final CorpusState state;

	public IngestCorpusService(ProtocolSource source, ProtocolChunkRepository chunks, LlmPort llm, ClockPort clock,
			ProtocolParser parser, CorpusState state) {
		this.source = source;
		this.chunks = chunks;
		this.llm = llm;
		this.clock = clock;
		this.parser = parser;
		this.state = state;
	}

	@Override
	public Mono<List<ProtocolChunk>> loadCorpus() {
		return source.load().map(documents -> {
			if (documents.isEmpty()) {
				throw new InvalidProtocolException("corpus", "no protocol files were found");
			}
			List<ProtocolChunk> corpus = documents.stream()
					.flatMap(document -> parser.parse(document.source(), document.markdown()).stream())
					.toList();
			Set<String> ids = new HashSet<>();
			corpus.stream().filter(chunk -> !ids.add(chunk.id())).findFirst().ifPresent(duplicate -> {
				throw new InvalidProtocolException("corpus", "the chunk " + duplicate.id() + " appears twice");
			});
			return corpus;
		});
	}

	@Override
	public Mono<IngestionReport> ingest(List<ProtocolChunk> corpus) {
		String model = llm.embeddingModel();
		Set<String> ids = corpus.stream().map(ProtocolChunk::id).collect(Collectors.toSet());
		return chunks.findAll().collectMap(StoredChunk::id, Function.identity())
				.flatMap(stored -> {
					List<ProtocolChunk> pending = corpus.stream()
							.filter(chunk -> !isCurrent(stored, chunk, model))
							.toList();
					// One embedding at a time: the corpus is small, the provider has rate limits, and a failure stops
					// the ingestion without other calls still in flight.
					return Flux.fromIterable(pending)
							.concatMap(chunk -> llm.embed(chunk.embeddingText())
									.flatMap(vector -> chunks.save(chunk, vector, model, clock.now())))
							.then(chunks.deleteAllExcept(ids))
							.map(removed -> new IngestionReport(corpus.size(), pending.size(), removed));
				})
				.doOnNext(state::ready);
	}

	private static boolean isCurrent(Map<String, StoredChunk> stored, ProtocolChunk chunk, String model) {
		StoredChunk existing = stored.get(chunk.id());
		return existing != null && existing.isCurrent(chunk, model);
	}

}
