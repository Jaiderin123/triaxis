package dev.triaxis.auth.application.port.out;

import java.time.Instant;

import dev.triaxis.auth.domain.model.IssuedToken;
import dev.triaxis.auth.domain.model.User;

public interface TokenIssuer {

	IssuedToken issue(User user, Instant issuedAt);

}
