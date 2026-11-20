package dev.triaxis.assistant.domain.model;

/** A cited protocol section, always taken from the corpus metadata, never from the model text. */
public record Source(String protocolId, String section) {
}
