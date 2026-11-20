package dev.triaxis.assistant;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.triaxis.security.TriaxisJwtDecoders;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Replaces the JWKS-based decoder with one bound to a test key pair, and signs tokens with the same
 * shape as the ones issued by the auth service.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

	public static final String ISSUER = "triaxis-auth";
	public static final String AUDIENCE = "triaxis";

	private static final KeyPair KEYS = generate();

	@Bean
	ReactiveJwtDecoder testJwtDecoder() {
		return TriaxisJwtDecoders.fromPublicKey((RSAPublicKey) KEYS.getPublic(), ISSUER, AUDIENCE);
	}

	public static String token(String subject, String... roles) {
		return sign(KEYS, subject, ISSUER, Instant.now().plusSeconds(600), roles);
	}

	public static String expiredToken(String subject, String... roles) {
		return sign(KEYS, subject, ISSUER, Instant.now().minusSeconds(3600), roles);
	}

	public static String tokenForAudience(String audience, String subject, String... roles) {
		return sign(KEYS, subject, ISSUER, audience, Instant.now().plusSeconds(600), roles);
	}

	public static String tokenSignedByAnotherKey(String subject, String... roles) {
		return sign(generate(), subject, ISSUER, Instant.now().plusSeconds(600), roles);
	}

	private static String sign(KeyPair pair, String subject, String issuer, Instant expiresAt, String... roles) {
		return sign(pair, subject, issuer, AUDIENCE, expiresAt, roles);
	}

	private static String sign(KeyPair pair, String subject, String issuer, String audience, Instant expiresAt,
			String... roles) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.subject(subject)
					.issuer(issuer)
					.audience(audience)
					.issueTime(new Date())
					.expirationTime(Date.from(expiresAt))
					.jwtID(UUID.randomUUID().toString())
					.claim("roles", List.of(roles))
					.build();
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
			jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
			return jwt.serialize();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static KeyPair generate() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
