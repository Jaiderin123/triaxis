package dev.triaxis.assistant.application.port.out;

import java.time.Instant;
import java.util.Set;

import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;
import dev.triaxis.assistant.domain.model.StoredChunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Stores the protocol sections with their embeddings and finds the closest ones to a question. */
public interface ProtocolChunkRepository {

	Flux<StoredChunk> findAll();

	/** Inserts the chunk or replaces it, together with its embedding and the model that produced it. */
	Mono<Void> save(ProtocolChunk chunk, float[] embedding, String embeddingModel, Instant now);

	/** Deletes every stored chunk whose id is not in {@code keep}, and returns how many were deleted. */
	Mono<Long> deleteAllExcept(Set<String> keep);

	/** The {@code limit} closest chunks embedded with {@code embeddingModel}, closest first. */
	Flux<RetrievedExcerpt> findNearest(float[] query, String embeddingModel, int limit);

}
