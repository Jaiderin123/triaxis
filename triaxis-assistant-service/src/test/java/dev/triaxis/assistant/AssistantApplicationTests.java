package dev.triaxis.assistant;

import java.time.Duration;

import dev.triaxis.assistant.application.port.out.ProtocolChunkRepository;
import dev.triaxis.assistant.application.service.CorpusState;
import dev.triaxis.assistant.domain.model.StoredChunk;
import dev.triaxis.assistant.infrastructure.adapter.out.llm.mock.MockLlmAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@AssistantIntegrationTest
class AssistantApplicationTests {

	@Autowired
	CorpusState corpus;

	@Autowired
	ProtocolChunkRepository chunks;

	@Test
	void theCorpusIsIngestedAtStartupWithTheMockProvider() throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while (!corpus.isReady() && System.nanoTime() < deadline) {
			Thread.sleep(50);
		}

		assertThat(corpus.isReady()).isTrue();
		assertThat(chunks.findAll().collectList().block())
				.hasSize(7)
				.allMatch(chunk -> chunk.embeddingModel().equals(MockLlmAdapter.EMBEDDING_MODEL))
				.extracting(StoredChunk::id)
				.contains("PROTO-001#medidas-iniciales", "PROTO-002#acciones-iniciales", "PROTO-003#rehidratacion");
	}

}
