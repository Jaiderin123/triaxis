package dev.triaxis.triage.domain.exception;

/** An invalid field, named with its domain name (the web layer translates it to the external name). */
public record FieldViolation(String field, String message) {
}
