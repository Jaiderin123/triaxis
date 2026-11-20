package dev.triaxis.web.security;

import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.problem.Problems;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 403 in the standard problem format. */
public class ProblemAccessDeniedHandler implements ServerAccessDeniedHandler {

	private final ProblemWriter writer;

	public ProblemAccessDeniedHandler(ProblemWriter writer) {
		this.writer = writer;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException error) {
		return writer.write(exchange, Problems.of(HttpStatus.FORBIDDEN, "Access denied"));
	}

}
