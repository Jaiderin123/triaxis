package dev.triaxis.auth;

import java.time.Duration;
import java.util.Set;

import dev.triaxis.auth.domain.model.IssuedToken;
import dev.triaxis.auth.domain.model.Role;
import dev.triaxis.auth.domain.model.User;
import dev.triaxis.auth.infrastructure.adapter.in.web.dto.TokenRequest;
import dev.triaxis.auth.infrastructure.adapter.in.web.dto.TokenResponse;
import dev.triaxis.auth.infrastructure.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Records that hold secrets never print them in toString. */
class SecretsAreNotPrintedTest {

	private static final String SECRET = "s3cr3t-value";

	@Test
	void theTokenRequestHidesThePassword() {
		assertThat(new TokenRequest("nurse", SECRET).toString()).doesNotContain(SECRET).contains("nurse");
	}

	@Test
	void theIssuedTokenHidesTheBearerToken() {
		assertThat(new IssuedToken(SECRET, 900).toString()).doesNotContain(SECRET).contains("accessToken=***");
		assertThat(new TokenResponse(SECRET, "Bearer", 900).toString()).doesNotContain(SECRET).contains("Bearer");
	}

	@Test
	void theUserHidesThePasswordHash() {
		assertThat(new User("nurse", SECRET, Set.of(Role.NURSE)).toString()).doesNotContain(SECRET).contains("nurse");
		assertThat(new AuthProperties.UserEntry("nurse", SECRET, Set.of(Role.NURSE)).toString())
				.doesNotContain(SECRET);
	}

	@Test
	void theJwtSettingsHideThePrivateKey() {
		AuthProperties.Jwt jwt = new AuthProperties.Jwt("triaxis-auth", "triaxis", Duration.ofMinutes(15),
				"-----BEGIN PRIVATE KEY-----" + SECRET, false);

		assertThat(jwt.toString()).doesNotContain(SECRET).doesNotContain("BEGIN").contains("privateKey=***");
	}

}
