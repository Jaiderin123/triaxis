package dev.triaxis.assistant.domain.model;

/**
 * The raw text of one protocol file.
 *
 * @param source    file name, used in error messages
 * @param markdown  file content
 */
public record ProtocolDocument(String source, String markdown) {
}
