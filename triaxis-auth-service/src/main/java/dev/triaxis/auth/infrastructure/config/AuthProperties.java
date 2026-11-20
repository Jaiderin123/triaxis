package dev.triaxis.auth.infrastructure.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import dev.triaxis.auth.domain.model.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the auth service. Nothing secret lives in the repository: the signing key comes from
 * the {@code JWT_PRIVATE_KEY} environment variable and users come from configuration.
 */
@ConfigurationProperties("auth")
public record AuthProperties(Jwt jwt, @DefaultValue List<UserEntry> users, @DefaultValue RateLimit rateLimit) {

	public record Jwt(
			@DefaultValue("triaxis-auth") String issuer,
			@DefaultValue("triaxis") String audience,
			@DefaultValue("PT15M") Duration ttl,
			/** PKCS#8 PEM private key; empty when it must be generated (local profile only). */
			@DefaultValue("") String privateKey,
			@DefaultValue("false") boolean generateKeyIfMissing) {
	}

	/** Attempts allowed on the token endpoint per username and per address in each window. */
	public record RateLimit(@DefaultValue("10") int maxAttempts, @DefaultValue("PT1M") Duration window) {
	}

	public record UserEntry(String username, String passwordHash, Set<Role> roles) {
	}

}
