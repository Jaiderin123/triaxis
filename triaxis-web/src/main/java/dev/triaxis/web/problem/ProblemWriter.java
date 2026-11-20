package dev.triaxis.web.problem;

import java.net.URI;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes every error response of every service in the same shape: RFC 7807 with {@code instance} (the path),
 * {@code traceId} and {@code timestamp}. The HTTP status line is set from the same value as the body field
 * {@code status}, so they cannot differ.
 */
public class ProblemWriter {

	private final ObjectMapper mapper;
	private final ObjectProvider<Tracer> tracer;

	public ProblemWriter(ObjectMapper mapper, ObjectProvider<Tracer> tracer) {
		this.mapper = mapper;
		this.tracer = tracer;
	}

	public Mono<Void> write(ServerWebExchange exchange, ProblemDetail problem) {
		ServerHttpResponse response = exchange.getResponse();
		if (response.isCommitted()) {
			return Mono.empty();
		}
		if (problem.getInstance() == null) {
			problem.setInstance(instance(exchange));
		}
		problem.setProperty("traceId", currentTraceId());
		problem.setProperty("timestamp", Instant.now().toString());
		response.setStatusCode(HttpStatus.valueOf(problem.getStatus()));
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		try {
			DataBuffer buffer = response.bufferFactory().wrap(mapper.writeValueAsBytes(problem));
			return response.writeWith(Mono.just(buffer));
		}
		catch (JsonProcessingException e) {
			return Mono.error(e);
		}
	}

	private static URI instance(ServerWebExchange exchange) {
		try {
			return URI.create(exchange.getRequest().getURI().getRawPath());
		}
		catch (IllegalArgumentException e) {
			return URI.create("/");
		}
	}

	/** The trace id of the current request, or null when there is no active span. */
	private String currentTraceId() {
		Tracer current = tracer.getIfAvailable();
		if (current == null) {
			return null;
		}
		Span span = current.currentSpan();
		return span == null ? null : span.context().traceId();
	}

}
