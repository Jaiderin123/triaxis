package dev.triaxis.security;

import java.time.Duration;

/**
 * Limits of the JWKS download.
 *
 * @param connectTimeout   longest wait to open the connection to the auth service
 * @param responseTimeout  longest wait for the JWKS answer
 * @param refreshCooldown  shortest time between two downloads caused by a token whose key id is unknown, so
 *                         tokens with random key ids cannot make every request download the JWKS again
 */
public record JwksSettings(Duration connectTimeout, Duration responseTimeout, Duration refreshCooldown) {

	public JwksSettings {
		requirePositive(connectTimeout, "connect-timeout");
		requirePositive(responseTimeout, "response-timeout");
		if (refreshCooldown == null || refreshCooldown.isNegative()) {
			throw new IllegalArgumentException("triaxis.security.jwks.refresh-cooldown must not be negative");
		}
	}

	public static JwksSettings defaults() {
		return new JwksSettings(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(30));
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("triaxis.security.jwks." + name + " must be positive");
		}
	}

}
