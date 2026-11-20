package dev.triaxis.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the shared JWT resource-server configuration.
 *
 * @param jwksUri URL of the JWKS endpoint of the auth service (fetched lazily)
 * @param issuer   expected value of the {@code iss} claim
 * @param audience expected value of the {@code aud} claim, so a token meant for another system is rejected
 */
@ConfigurationProperties("triaxis.security")
public record TriaxisSecurityProperties(String jwksUri, @DefaultValue("triaxis-auth") String issuer,
		@DefaultValue("triaxis") String audience) {
}
