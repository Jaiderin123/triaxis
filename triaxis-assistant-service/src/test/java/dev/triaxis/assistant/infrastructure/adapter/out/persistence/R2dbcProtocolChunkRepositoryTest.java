package dev.triaxis.assistant.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.triaxis.assistant.AssistantIntegrationTest;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;
import dev.triaxis.assistant.domain.model.StoredChunk;
import dev.triaxis.assistant.application.port.in.IngestCorpusUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** pgvector through R2DBC against a real PostgreSQL, with hand-made vectors so distances are known. */
@AssistantIntegrationTest
class R2dbcProtocolChunkRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

	@Autowired
	R2dbcProtocolChunkRepository repository;

	@Autowired
	DatabaseClient database;

	@Autowired
	IngestCorpusUseCase ingestion;

	private static ProtocolChunk chunk(String id, String section) {
		return ProtocolChunk.of(id, id.substring(0, 9), "Protocolo", section, "Texto de " + section);
	}

	@BeforeEach
	void emptyTheTable() {
		database.sql("DELETE FROM protocol_chunks").fetch().rowsUpdated().block();
	}

	/** Other tests share the database and need the real corpus back. */
	@AfterEach
	void restoreTheCorpus() {
		database.sql("DELETE FROM protocol_chunks").fetch().rowsUpdated().block();
		ingestion.loadCorpus().flatMap(ingestion::ingest).block();
	}

	@Test
	void findsTheClosestChunksFirstWithTheirCosineDistance() {
		repository.save(chunk("PROTO-001#a", "A"), new float[] {1, 0, 0}, "m1", NOW).block();
		repository.save(chunk("PROTO-001#b", "B"), new float[] {0, 1, 0}, "m1", NOW).block();
		repository.save(chunk("PROTO-002#c", "C"), new float[] {1, 1, 0}, "m1", NOW).block();

		List<RetrievedExcerpt> nearest = repository.findNearest(new float[] {1, 0, 0}, "m1", 2).collectList().block();

		assertThat(nearest).extracting(excerpt -> excerpt.chunk().id()).containsExactly("PROTO-001#a", "PROTO-002#c");
		assertThat(nearest.get(0).distance()).isCloseTo(0.0, within(1e-6));
		assertThat(nearest.get(1).distance()).isCloseTo(1 - Math.sqrt(0.5), within(1e-6));
		ProtocolChunk read = nearest.get(0).chunk();
		assertThat(read).isEqualTo(chunk("PROTO-001#a", "A"));
	}

	@Test
	void searchesOnlyTheChunksOfTheGivenModel() {
		repository.save(chunk("PROTO-001#a", "A"), new float[] {1, 0}, "m1", NOW).block();
		repository.save(chunk("PROTO-001#b", "B"), new float[] {1, 0, 0}, "m2", NOW).block();

		StepVerifier.create(repository.findNearest(new float[] {1, 0, 0}, "m2", 5))
				.assertNext(excerpt -> assertThat(excerpt.chunk().id()).isEqualTo("PROTO-001#b"))
				.verifyComplete();
	}

	@Test
	void savingAgainReplacesTheChunkAndItsEmbedding() {
		repository.save(chunk("PROTO-001#a", "A"), new float[] {1, 0}, "m1", NOW).block();
		ProtocolChunk changed = ProtocolChunk.of("PROTO-001#a", "PROTO-001", "Protocolo", "A", "Texto nuevo");
		repository.save(changed, new float[] {0, 1, 0}, "m2", NOW.plusSeconds(60)).block();

		assertThat(repository.findAll().collectList().block())
				.containsExactly(new StoredChunk("PROTO-001#a", changed.contentHash(), "m2"));
		StepVerifier.create(repository.findNearest(new float[] {0, 1, 0}, "m2", 1))
				.assertNext(excerpt -> assertThat(excerpt.chunk().content()).isEqualTo("Texto nuevo"))
				.verifyComplete();
	}

	@Test
	void deletesEverythingThatIsNotKept() {
		repository.save(chunk("PROTO-001#a", "A"), new float[] {1, 0}, "m1", NOW).block();
		repository.save(chunk("PROTO-001#b", "B"), new float[] {0, 1}, "m1", NOW).block();

		StepVerifier.create(repository.deleteAllExcept(Set.of("PROTO-001#a"))).expectNext(1L).verifyComplete();
		StepVerifier.create(repository.deleteAllExcept(Set.of())).expectNext(1L).verifyComplete();
		assertThat(repository.findAll().collectList().block()).isEmpty();
	}

}
