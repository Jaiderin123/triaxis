package dev.triaxis.auth.application.port.in;

import dev.triaxis.auth.domain.model.IssuedToken;
import reactor.core.publisher.Mono;

public interface IssueTokenUseCase {

	/** Issues a token, or fails with {@code InvalidCredentialsException}. */
	Mono<IssuedToken> issue(String username, String password);

}
