package dev.triaxis.web.problem;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builders of the problem details every service returns. */
public final class Problems {

	private Problems() {
	}

	public static ProblemDetail of(HttpStatus status, String detail) {
		return ProblemDetail.forStatusAndDetail(status, detail);
	}

	/** A 400 that lists every invalid field (property {@code errores}). */
	public static ProblemDetail validation(List<FieldProblem> fields) {
		String detail = "Invalid field(s): " + fields.stream()
				.map(f -> f.campo() + " " + f.mensaje()).reduce((a, b) -> a + "; " + b).orElse("");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setProperty("errores", List.copyOf(fields));
		return problem;
	}

}
