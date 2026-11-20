package dev.triaxis.web.security;

import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.problem.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 401 in the standard problem format. The message is generic so it reveals nothing about the cause. */
public class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	private final ProblemWriter writer;
	private final String fixedChallenge;

	/** Bearer token authentication (the default for the APIs). */
	public ProblemAuthenticationEntryPoint(ProblemWriter writer) {
		this(writer, null);
	}

	/** Always answers with the given {@code WWW-Authenticate} challenge, for example HTTP Basic. */
	public ProblemAuthenticationEntryPoint(ProblemWriter writer, String fixedChallenge) {
		this.writer = writer;
		this.fixedChallenge = fixedChallenge;
	}

	@Override
	public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException error) {
		boolean tokenSent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null;
		String challenge = fixedChallenge != null ? fixedChallenge
				: tokenSent ? "Bearer error=\"invalid_token\"" : "Bearer";
		exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge);
		return writer.write(exchange, Problems.of(HttpStatus.UNAUTHORIZED, "Authentication is required"));
	}

}
