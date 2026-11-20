package dev.triaxis.assistant.domain.model;

/**
 * Result of one ingestion of the corpus.
 *
 * @param chunks    sections in the corpus
 * @param embedded  sections embedded and stored because they were new or changed
 * @param removed   stored sections that are no longer in the corpus
 */
public record IngestionReport(int chunks, int embedded, long removed) {
}
