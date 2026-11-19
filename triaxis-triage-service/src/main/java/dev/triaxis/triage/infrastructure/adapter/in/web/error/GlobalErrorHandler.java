package dev.triaxis.triage.infrastructure.adapter.in.web.error;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.triage.domain.exception.ConcurrentStatusChangeException;
import dev.triaxis.triage.domain.exception.FieldViolation;
import dev.triaxis.triage.domain.exception.InvalidPatientDataException;
import dev.triaxis.triage.domain.exception.InvalidStatusTransitionException;
import dev.triaxis.triage.domain.exception.PatientNotFoundException;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ExternalFieldNames;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ExternalValues;
import dev.triaxis.triage.infrastructure.adapter.in.web.validation.InvalidRequestException;
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

/**
 * Maps exceptions to RFC 7807 {@code ProblemDetail} responses (functional endpoints have no controller advice).
 * Field names in the answers are the names of the JSON contract.
 */
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
		if (ex instanceof InvalidPatientDataException invalid) {
			problem = validationProblem(invalid.violations().stream()
					.map(v -> new FieldViolation(ExternalFieldNames.of(v.field()), v.message())).toList());
		}
		else if (ex instanceof InvalidRequestException invalid) {
			problem = validationProblem(invalid.violations());
		}
		else if (ex instanceof PatientNotFoundException) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		}
		else if (ex instanceof InvalidStatusTransitionException transition) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, transitionMessage(transition));
		}
		else if (ex instanceof ConcurrentStatusChangeException) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		}
		else if (ex instanceof UnsupportedMediaTypeStatusException) {
			problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"The request body must be application/json");
		}
		else if (ex instanceof ServerWebInputException input) {
			problem = badBody(input);
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

	/** Same message as the domain one, but with the status names of the JSON contract. */
	private static String transitionMessage(InvalidStatusTransitionException ex) {
		String allowed = ex.from().allowedTransitions().stream()
				.map(ExternalValues::status).sorted().reduce((a, b) -> a + ", " + b).orElse("none");
		return "Cannot change the status from " + ExternalValues.status(ex.from()) + " to "
				+ ExternalValues.status(ex.to()) + ". Allowed from " + ExternalValues.status(ex.from()) + ": " + allowed;
	}

	private static ProblemDetail validationProblem(List<FieldViolation> violations) {
		String detail = "Invalid field(s): " + violations.stream()
				.map(v -> v.field() + " " + v.message()).reduce((a, b) -> a + "; " + b).orElse("");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setProperty("errores", violations.stream()
				.map(v -> Map.of("campo", v.field(), "mensaje", v.message())).toList());
		return problem;
	}

	/** Names the offending field when the JSON could be read but a value has the wrong type. */
	private static ProblemDetail badBody(ServerWebInputException input) {
		for (Throwable cause = input; cause != null; cause = cause.getCause()) {
			if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
				String field = mapping.getPath().get(0).getFieldName();
				if (field != null) {
					return validationProblem(List.of(new FieldViolation(field, "has an invalid value or type")));
				}
			}
		}
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
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
