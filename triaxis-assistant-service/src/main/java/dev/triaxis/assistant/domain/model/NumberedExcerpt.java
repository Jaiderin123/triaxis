package dev.triaxis.assistant.domain.model;

/**
 * An excerpt as the model sees it: the model cites excerpts by {@code number}, never by protocol name, so a
 * citation can be checked against what was retrieved.
 */
public record NumberedExcerpt(int number, ProtocolChunk chunk) {
}
