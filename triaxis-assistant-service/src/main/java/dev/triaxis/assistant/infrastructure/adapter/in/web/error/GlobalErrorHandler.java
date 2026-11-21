package dev.triaxis.assistant.infrastructure.adapter.in.web.error;

import java.util.List;

import dev.triaxis.assistant.domain.exception.AssistantUnavailableException;
import dev.triaxis.assistant.domain.exception.InvalidQuestionException;
import dev.triaxis.assistant.domain.exception.UrgencyViewUnavailableException;
import dev.triaxis.assistant.infrastructure.adapter.in.web.validation.InvalidRequestException;
import dev.triaxis.assistant.infrastructure.config.LlmProperties;
import dev.triaxis.web.problem.AbstractProblemExceptionHandler;
import dev.triaxis.web.problem.FieldProblem;
import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.problem.Problems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Maps the exceptions of the assistant to problems; everything else is handled by the shared base class. An
 * unavailable provider is a 503 with {@code Retry-After} and never a generated answer (ADR 0004).
 */
@Component
@Order(-2)
class GlobalErrorHandler extends AbstractProblemExceptionHandler {

	static final String UNAVAILABLE = "The clinical assistant is temporarily unavailable";

	private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

	private final long retryAfterSeconds;

	GlobalErrorHandler(ProblemWriter writer, LlmProperties properties) {
		super(writer);
		this.retryAfterSeconds = properties.timeout().retryAfter().toSeconds();
	}

	@Override
	protected ProblemDetail mapDomain(Throwable error) {
		if (error instanceof InvalidQuestionException invalid) {
			return Problems.validation(List.of(new FieldProblem("pregunta", invalid.getMessage())));
		}
		if (error instanceof InvalidRequestException invalid) {
			return Problems.validation(List.of(new FieldProblem(invalid.field(), invalid.getMessage())));
		}
		if (error instanceof AssistantUnavailableException unavailable) {
			log.warn("Question answered with 503: {}", unavailable.getMessage());
			return Problems.of(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
		}
		if (error instanceof UrgencyViewUnavailableException unavailable) {
			log.warn("Urgency view answered with 503: {}", unavailable.getCause().getClass().getSimpleName());
			return Problems.of(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage());
		}
		return null;
	}

	@Override
	protected void addHeaders(Throwable error, HttpHeaders headers) {
		if (error instanceof AssistantUnavailableException || error instanceof UrgencyViewUnavailableException) {
			headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
		}
	}

}
