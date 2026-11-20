package dev.triaxis.triage.infrastructure.adapter.in.web.error;

import java.util.List;

import dev.triaxis.triage.domain.exception.ConcurrentStatusChangeException;
import dev.triaxis.triage.domain.exception.FieldViolation;
import dev.triaxis.triage.domain.exception.InvalidPatientDataException;
import dev.triaxis.triage.domain.exception.InvalidStatusTransitionException;
import dev.triaxis.triage.domain.exception.PatientNotFoundException;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ExternalFieldNames;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ExternalValues;
import dev.triaxis.triage.infrastructure.adapter.in.web.validation.InvalidRequestException;
import dev.triaxis.web.problem.AbstractProblemExceptionHandler;
import dev.triaxis.web.problem.FieldProblem;
import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.problem.Problems;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Maps the exceptions of the triage service to problems. Everything else (malformed bodies, unknown routes,
 * unexpected errors) is handled by the shared base class. Field names in the answers are the names of the
 * JSON contract.
 */
@Component
@Order(-2)
class GlobalErrorHandler extends AbstractProblemExceptionHandler {

	GlobalErrorHandler(ProblemWriter writer) {
		super(writer);
	}

	@Override
	protected ProblemDetail mapDomain(Throwable error) {
		if (error instanceof InvalidPatientDataException invalid) {
			return Problems.validation(fields(invalid.violations(), true));
		}
		if (error instanceof InvalidRequestException invalid) {
			return Problems.validation(fields(invalid.violations(), false));
		}
		if (error instanceof PatientNotFoundException) {
			return Problems.of(HttpStatus.NOT_FOUND, error.getMessage());
		}
		if (error instanceof InvalidStatusTransitionException transition) {
			return Problems.of(HttpStatus.CONFLICT, transitionMessage(transition));
		}
		if (error instanceof ConcurrentStatusChangeException) {
			return Problems.of(HttpStatus.CONFLICT, error.getMessage());
		}
		return null;
	}

	/** Domain violations use domain field names; request violations already use the external ones. */
	private static List<FieldProblem> fields(List<FieldViolation> violations, boolean translate) {
		return violations.stream()
				.map(v -> new FieldProblem(translate ? ExternalFieldNames.of(v.field()) : v.field(), v.message()))
				.toList();
	}

	/** Same message as the domain one, but with the status names of the JSON contract. */
	private static String transitionMessage(InvalidStatusTransitionException ex) {
		String allowed = ex.from().allowedTransitions().stream()
				.map(ExternalValues::status).sorted().reduce((a, b) -> a + ", " + b).orElse("none");
		return "Cannot change the status from " + ExternalValues.status(ex.from()) + " to "
				+ ExternalValues.status(ex.to()) + ". Allowed from " + ExternalValues.status(ex.from()) + ": " + allowed;
	}

}
