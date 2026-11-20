package dev.triaxis.assistant.infrastructure.adapter.in.startup;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.assistant.application.port.in.IngestCorpusUseCase;
import dev.triaxis.assistant.domain.exception.InvalidProtocolException;
import dev.triaxis.assistant.domain.model.IngestionReport;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.infrastructure.config.CorpusProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorpusIngestionRunnerTest {

	private static final CorpusProperties FAST = new CorpusProperties("classpath:/protocols/",
			new CorpusProperties.Retry(Duration.ofMillis(10), Duration.ofMillis(20)));

	private static final ProtocolChunk CHUNK = ProtocolChunk.of("PROTO-001#a", "PROTO-001", "P", "A", "Texto.");

	@Test
	void aMalformedCorpusStopsTheStartup() {
		IngestCorpusUseCase broken = new IngestCorpusUseCase() {
			@Override
			public Mono<List<ProtocolChunk>> loadCorpus() {
				return Mono.error(new InvalidProtocolException("broken.md", "no title"));
			}

			@Override
			public Mono<IngestionReport> ingest(List<ProtocolChunk> corpus) {
				throw new AssertionError("must not run");
			}
		};

		assertThatThrownBy(() -> new CorpusIngestionRunner(broken, FAST).run(null))
				.isInstanceOf(InvalidProtocolException.class);
	}

	@Test
	void theEmbeddingIsRetriedInTheBackgroundUntilItWorks() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		IngestCorpusUseCase flaky = new IngestCorpusUseCase() {
			@Override
			public Mono<List<ProtocolChunk>> loadCorpus() {
				return Mono.just(List.of(CHUNK));
			}

			@Override
			public Mono<IngestionReport> ingest(List<ProtocolChunk> corpus) {
				return Mono.defer(() -> attempts.incrementAndGet() < 3
						? Mono.error(new IllegalStateException("provider down"))
						: Mono.just(new IngestionReport(1, 1, 0)));
			}
		};
		CorpusIngestionRunner runner = new CorpusIngestionRunner(flaky, FAST);

		runner.run(null);
		for (int i = 0; i < 100 && attempts.get() < 3; i++) {
			Thread.sleep(10);
		}
		runner.destroy();

		assertThat(attempts.get()).isEqualTo(3);
	}

}
