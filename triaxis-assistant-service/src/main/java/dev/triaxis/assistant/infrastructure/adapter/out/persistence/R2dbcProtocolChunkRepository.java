package dev.triaxis.assistant.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;
import dev.triaxis.assistant.domain.model.StoredChunk;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * pgvector through plain R2DBC (ADR 0002): vectors travel as text and are cast with {@code ::vector}, so the
 * whole path stays reactive. {@code <=>} is the cosine distance.
 */
@Repository
class R2dbcProtocolChunkRepository implements ProtocolChunkRepository {

	private static final String UPSERT = """
			INSERT INTO protocol_chunks (id, protocol_id, protocol_title, section, content, content_hash,
			                             embedding_model, embedding, created_at, updated_at)
			VALUES (:id, :protocolId, :protocolTitle, :section, :content, :contentHash,
			        :model, CAST(:embedding AS vector), :now, :now)
			ON CONFLICT (id) DO UPDATE SET
			  protocol_id = EXCLUDED.protocol_id,
			  protocol_title = EXCLUDED.protocol_title,
			  section = EXCLUDED.section,
			  content = EXCLUDED.content,
			  content_hash = EXCLUDED.content_hash,
			  embedding_model = EXCLUDED.embedding_model,
			  embedding = EXCLUDED.embedding,
			  updated_at = EXCLUDED.updated_at
			""";

	private static final String NEAREST = """
			SELECT id, protocol_id, protocol_title, section, content, content_hash,
			       embedding <=> CAST(:query AS vector) AS distance
			FROM protocol_chunks
			WHERE embedding_model = :model
			ORDER BY distance, id
			LIMIT :limit
			""";

	private final DatabaseClient client;

	R2dbcProtocolChunkRepository(DatabaseClient client) {
		this.client = client;
	}

	@Override
	public Flux<StoredChunk> findAll() {
		return client.sql("SELECT id, content_hash, embedding_model FROM protocol_chunks")
				.map(row -> new StoredChunk(row.get("id", String.class), row.get("content_hash", String.class),
						row.get("embedding_model", String.class)))
				.all();
	}

	@Override
	public Mono<Void> save(ProtocolChunk chunk, float[] embedding, String embeddingModel, Instant now) {
		return client.sql(UPSERT)
				.bind("id", chunk.id())
				.bind("protocolId", chunk.protocolId())
				.bind("protocolTitle", chunk.protocolTitle())
				.bind("section", chunk.section())
				.bind("content", chunk.content())
				.bind("contentHash", chunk.contentHash())
				.bind("model", embeddingModel)
				.bind("embedding", VectorText.of(embedding))
				.bind("now", now.atOffset(ZoneOffset.UTC))
				.fetch().rowsUpdated().then();
	}

	@Override
	public Mono<Long> deleteAllExcept(Set<String> keep) {
		if (keep.isEmpty()) {
			return client.sql("DELETE FROM protocol_chunks").fetch().rowsUpdated();
		}
		return client.sql("DELETE FROM protocol_chunks WHERE NOT (id = ANY(:keep))")
				.bind("keep", keep.toArray(String[]::new))
				.fetch().rowsUpdated();
	}

	@Override
	public Flux<RetrievedExcerpt> findNearest(float[] query, String embeddingModel, int limit) {
		return client.sql(NEAREST)
				.bind("query", VectorText.of(query))
				.bind("model", embeddingModel)
				.bind("limit", limit)
				.map(R2dbcProtocolChunkRepository::toExcerpt)
				.all();
	}

	private static RetrievedExcerpt toExcerpt(Readable row) {
		ProtocolChunk chunk = new ProtocolChunk(row.get("id", String.class), row.get("protocol_id", String.class),
				row.get("protocol_title", String.class), row.get("section", String.class),
				row.get("content", String.class), row.get("content_hash", String.class));
		return new RetrievedExcerpt(chunk, row.get("distance", Double.class));
	}

}
