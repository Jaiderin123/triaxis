package dev.triaxis.web.problem;

import java.util.List;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Base of the global exception handler of a service. A service only says how its own exceptions map to a
 * problem ({@link #mapDomain}); everything else (malformed bodies, unsupported media types, unknown routes,
 * oversized bodies and unexpected errors) is handled here, the same way everywhere. Security exceptions are
 * left to Spring Security, which answers through the shared entry point and denied handler.
 */
public abstract class AbstractProblemExceptionHandler implements WebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(AbstractProblemExceptionHandler.class);

	private final ProblemWriter writer;

	protected AbstractProblemExceptionHandler(ProblemWriter writer) {
		this.writer = writer;
	}

	/** Maps an exception of the service to a problem, or returns null when it is not one of its own. */
	protected abstract ProblemDetail mapDomain(Throwable error);

	/** Lets a service add headers to the answer for one of its own exceptions (for example {@code Retry-After}). */
	protected void addHeaders(Throwable error, HttpHeaders headers) {
	}

	@Override
	public final Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
		if (isSecurityException(error)) {
			return Mono.error(error);
		}
		ProblemDetail problem = mapDomain(error);
		if (problem == null) {
			problem = mapCommon(error);
		}
		else {
			addHeaders(error, exchange.getResponse().getHeaders());
		}
		return writer.write(exchange, problem);
	}

	private ProblemDetail mapCommon(Throwable error) {
		if (hasCause(error, DataBufferLimitException.class)) {
			return Problems.of(HttpStatus.PAYLOAD_TOO_LARGE, "The request body is too large");
		}
		if (error instanceof UnsupportedMediaTypeStatusException) {
			return Problems.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request body must be application/json");
		}
		if (error instanceof ServerWebInputException input) {
			return badBody(input);
		}
		if (error instanceof ResponseStatusException status) {
			HttpStatusCode code = status.getStatusCode();
			HttpStatus known = HttpStatus.resolve(code.value());
			String reason = known == null ? "Error" : known.getReasonPhrase();
			String detail = code.is5xxServerError() || status.getReason() == null ? reason : status.getReason();
			return Problems.of(known == null ? HttpStatus.INTERNAL_SERVER_ERROR : known, detail);
		}
		log.error("Unexpected error", error);
		return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
	}

	/** Names the offending field when the JSON could be read but a value has the wrong type. */
	private ProblemDetail badBody(ServerWebInputException input) {
		for (Throwable cause = input; cause != null; cause = cause.getCause()) {
			if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
				String field = mapping.getPath().get(0).getFieldName();
				if (field != null) {
					return Problems.validation(List.of(new FieldProblem(field, "has an invalid value or type")));
				}
			}
		}
		return Problems.of(HttpStatus.BAD_REQUEST, "Malformed request body");
	}

	private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSecurityException(Throwable error) {
		for (Class<?> type = error.getClass(); type != null; type = type.getSuperclass()) {
			String name = type.getName();
			if (name.equals("org.springframework.security.access.AccessDeniedException")
					|| name.equals("org.springframework.security.core.AuthenticationException")) {
				return true;
			}
		}
		return false;
	}

}
