package dev.triaxis.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the shared JWT resource-server configuration.
 *
 * @param jwksUri URL of the JWKS endpoint of the auth service (fetched lazily)
 * @param issuer   expected value of the {@code iss} claim
 * @param audience expected value of the {@code aud} claim, so a token meant for another system is rejected
 * @param jwks     timeouts and refresh limit of the JWKS download
 */
@ConfigurationProperties("triaxis.security")
public record TriaxisSecurityProperties(String jwksUri, @DefaultValue("triaxis-auth") String issuer,
		@DefaultValue("triaxis") String audience, @DefaultValue Jwks jwks) {

	/** {@code triaxis.security.jwks.*}; the defaults are those of {@link JwksSettings#defaults()}. */
	public record Jwks(@DefaultValue("PT2S") Duration connectTimeout, @DefaultValue("PT3S") Duration responseTimeout,
			@DefaultValue("PT30S") Duration refreshCooldown) {

		public JwksSettings settings() {
			return new JwksSettings(connectTimeout, responseTimeout, refreshCooldown);
		}

	}

}
