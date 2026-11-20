package dev.triaxis.assistant.application.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.triaxis.assistant.domain.model.IngestionReport;

/**
 * Whether the corpus is stored and embedded with the current model. Until then a question cannot be answered
 * honestly, so it gets a 503 instead of the "no protocol" notice (ASSUMPTIONS A37).
 */
public class CorpusState {

	private final AtomicReference<IngestionReport> lastIngestion = new AtomicReference<>();

	public boolean isReady() {
		return lastIngestion.get() != null;
	}

	public Optional<IngestionReport> lastIngestion() {
		return Optional.ofNullable(lastIngestion.get());
	}

	void ready(IngestionReport report) {
		lastIngestion.set(report);
	}

}
