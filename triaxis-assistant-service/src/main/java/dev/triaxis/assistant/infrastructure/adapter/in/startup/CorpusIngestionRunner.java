package dev.triaxis.assistant.infrastructure.adapter.in.startup;

import java.time.Duration;
import java.util.List;

import dev.triaxis.assistant.application.port.in.IngestCorpusUseCase;
import dev.triaxis.assistant.domain.model.ProtocolChunk;
import dev.triaxis.assistant.infrastructure.config.CorpusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.util.retry.Retry;

/**
 * Loads the corpus at startup (ASSUMPTIONS A37). The files are read and validated before the service accepts
 * traffic, so a malformed protocol stops the startup. The embedding runs in the background and is retried with
 * back-off, so an unavailable provider does not stop the service: the urgency view keeps working and questions
 * get a 503 until the corpus is ready.
 */
@Component
class CorpusIngestionRunner implements ApplicationRunner, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(CorpusIngestionRunner.class);
	private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(30);

	private final IngestCorpusUseCase ingestion;
	private final CorpusProperties properties;
	private Disposable running;

	CorpusIngestionRunner(IngestCorpusUseCase ingestion, CorpusProperties properties) {
		this.ingestion = ingestion;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		// Runs on the main thread during startup, where blocking is allowed.
		List<ProtocolChunk> corpus = ingestion.loadCorpus().block(LOAD_TIMEOUT);
		log.info("Corpus loaded: {} sections", corpus.size());
		CorpusProperties.Retry retry = properties.retry();
		running = ingestion.ingest(corpus)
				.retryWhen(Retry.backoff(Long.MAX_VALUE, retry.initialBackoff()).maxBackoff(retry.maxBackoff())
						.doBeforeRetry(signal -> log.warn("Corpus embedding failed (attempt {}), retrying: {}",
								signal.totalRetries() + 1, signal.failure().toString())))
				.subscribe(report -> log.info("Corpus ready: {} sections, {} embedded, {} removed", report.chunks(),
						report.embedded(), report.removed()));
	}

	@Override
	public void destroy() {
		if (running != null) {
			running.dispose();
		}
	}

}
