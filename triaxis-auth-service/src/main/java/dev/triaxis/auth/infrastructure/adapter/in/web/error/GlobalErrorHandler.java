package dev.triaxis.auth.infrastructure.adapter.in.web.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.auth.domain.exception.InvalidCredentialsException;
import dev.triaxis.auth.infrastructure.adapter.in.web.validation.RequestValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/** Maps exceptions to RFC 7807 {@code ProblemDetail} responses (functional endpoints have no controller advice). */
@Component
@Order(-2)
class GlobalErrorHandler implements WebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

	private final ObjectMapper mapper;

	GlobalErrorHandler(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		ProblemDetail problem;
		if (ex instanceof InvalidCredentialsException) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		else if (ex instanceof RequestValidationException validation) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, validation.getMessage());
		}
		else if (ex instanceof ServerWebInputException || ex instanceof UnsupportedMediaTypeStatusException) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
		}
		else if (ex instanceof AccessDeniedException || ex instanceof ResponseStatusException) {
			return Mono.error(ex);
		}
		else {
			log.error("Unexpected error", ex);
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
		}
		return write(exchange, problem);
	}

	private Mono<Void> write(ServerWebExchange exchange, ProblemDetail problem) {
		ServerHttpResponse response = exchange.getResponse();
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

}
