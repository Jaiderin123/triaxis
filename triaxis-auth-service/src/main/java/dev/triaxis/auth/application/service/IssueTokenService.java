package dev.triaxis.auth.application.service;

import dev.triaxis.auth.application.port.in.IssueTokenUseCase;
import dev.triaxis.auth.application.port.out.ClockPort;
import dev.triaxis.auth.application.port.out.PasswordVerifier;
import dev.triaxis.auth.application.port.out.TokenIssuer;
import dev.triaxis.auth.application.port.out.UserRepository;
import dev.triaxis.auth.domain.exception.InvalidCredentialsException;
import dev.triaxis.auth.domain.model.IssuedToken;
import dev.triaxis.auth.domain.model.User;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class IssueTokenService implements IssueTokenUseCase {

	private final UserRepository users;
	private final PasswordVerifier passwords;
	private final TokenIssuer tokens;
	private final ClockPort clock;

	public IssueTokenService(UserRepository users, PasswordVerifier passwords, TokenIssuer tokens, ClockPort clock) {
		this.users = users;
		this.passwords = passwords;
		this.tokens = tokens;
		this.clock = clock;
	}

	@Override
	public Mono<IssuedToken> issue(String username, String password) {
		return users.findByUsername(username)
				.map(java.util.Optional::of)
				.defaultIfEmpty(java.util.Optional.empty())
				.flatMap(found -> verifyAndSign(found.orElse(null), password))
				// Password hashing and signing are CPU-bound: keep them off the event loop.
				.subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<IssuedToken> verifyAndSign(User user, String password) {
		boolean valid = passwords.verify(password, user == null ? null : user.passwordHash());
		if (!valid || user == null) {
			return Mono.error(new InvalidCredentialsException());
		}
		return Mono.fromSupplier(() -> tokens.issue(user, clock.now()));
	}

}
