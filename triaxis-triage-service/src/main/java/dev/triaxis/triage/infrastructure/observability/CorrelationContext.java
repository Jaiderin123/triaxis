package dev.triaxis.triage.infrastructure.observability;

/** Names used to carry the correlation id of a request through the Reactor context and the HTTP headers. */
public final class CorrelationContext {

	/** Key in the Reactor context. */
	public static final String KEY = "correlationId";

	/** HTTP header accepted from the caller and echoed in the response. */
	public static final String HEADER = "X-Correlation-Id";

	private CorrelationContext() {
	}

}
