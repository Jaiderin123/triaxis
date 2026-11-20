package dev.triaxis.auth.infrastructure.adapter.in.web.error;

import dev.triaxis.auth.domain.exception.InvalidCredentialsException;
import dev.triaxis.auth.infrastructure.adapter.in.web.ratelimit.TooManyAttemptsException;
import dev.triaxis.auth.infrastructure.adapter.in.web.validation.RequestValidationException;
import dev.triaxis.web.problem.AbstractProblemExceptionHandler;
import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.problem.Problems;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Maps the exceptions of the auth service to problems; the shared base class handles the rest. */
@Component
@Order(-2)
class GlobalErrorHandler extends AbstractProblemExceptionHandler {

	GlobalErrorHandler(ProblemWriter writer) {
		super(writer);
	}

	@Override
	protected ProblemDetail mapDomain(Throwable error) {
		if (error instanceof InvalidCredentialsException) {
			return Problems.of(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		if (error instanceof RequestValidationException validation) {
			return Problems.validation(validation.fields());
		}
		if (error instanceof TooManyAttemptsException) {
			return Problems.of(HttpStatus.TOO_MANY_REQUESTS, error.getMessage());
		}
		return null;
	}

	@Override
	protected void addHeaders(Throwable error, HttpHeaders headers) {
		if (error instanceof TooManyAttemptsException tooMany) {
			headers.set(HttpHeaders.RETRY_AFTER, Long.toString(tooMany.retryAfterSeconds()));
		}
	}

}
