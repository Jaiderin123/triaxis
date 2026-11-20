package dev.triaxis.assistant.application.port.in;

import java.util.List;

import dev.triaxis.assistant.domain.model.IngestionReport;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import reactor.core.publisher.Mono;

/** Keeps the stored corpus in line with the protocol files. Running it twice changes nothing the second time. */
public interface IngestCorpusUseCase {

	/**
	 * Reads and parses every protocol file. Fails with {@code InvalidProtocolException} when a file is
	 * malformed, so a broken corpus is noticed at startup.
	 */
	Mono<List<ProtocolChunk>> loadCorpus();

	/** Embeds and stores new or changed sections and removes the ones that left the corpus. */
	Mono<IngestionReport> ingest(List<ProtocolChunk> corpus);

}
