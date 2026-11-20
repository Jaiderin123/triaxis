package dev.triaxis.assistant.domain.model;

/**
 * A chunk returned by the similarity search.
 *
 * @param chunk     the protocol section
 * @param distance  cosine distance to the question (0 is identical, 2 is opposite)
 */
public record RetrievedExcerpt(ProtocolChunk chunk, double distance) {
}
