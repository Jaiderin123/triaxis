package dev.triaxis.auth.infrastructure.adapter.in.web.handler;

import java.util.concurrent.TimeUnit;

import dev.triaxis.auth.application.port.in.IssueTokenUseCase;
import dev.triaxis.auth.infrastructure.adapter.in.web.dto.TokenRequest;
import dev.triaxis.auth.infrastructure.adapter.in.web.dto.TokenResponse;
import dev.triaxis.auth.infrastructure.adapter.in.web.validation.RequestValidationException;
import dev.triaxis.auth.infrastructure.adapter.in.web.validation.RequestValidator;
import dev.triaxis.auth.infrastructure.adapter.out.jwt.SigningKeyProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AuthHandler {

	private final IssueTokenUseCase issueToken;
	private final RequestValidator validator;
	private final SigningKeyProvider keys;

	AuthHandler(IssueTokenUseCase issueToken, RequestValidator validator, SigningKeyProvider keys) {
		this.issueToken = issueToken;
		this.validator = validator;
		this.keys = keys;
	}

	public Mono<ServerResponse> token(ServerRequest request) {
		return request.bodyToMono(TokenRequest.class)
				.switchIfEmpty(Mono.error(new RequestValidationException(java.util.List.of("body"))))
				.map(validator::validate)
				.flatMap(body -> issueToken.issue(body.username(), body.password()))
				.flatMap(issued -> ServerResponse.ok()
						.contentType(MediaType.APPLICATION_JSON)
						.cacheControl(CacheControl.noStore())
						.bodyValue(new TokenResponse(issued.accessToken(), "Bearer", issued.expiresInSeconds())));
	}

	public Mono<ServerResponse> jwks(ServerRequest request) {
		return ServerResponse.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
				.bodyValue(keys.publicKeySet().toString());
	}

}
