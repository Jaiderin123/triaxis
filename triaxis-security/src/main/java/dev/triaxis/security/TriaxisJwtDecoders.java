package dev.triaxis.security;

import java.security.interfaces.RSAPublicKey;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Builds the decoders that accept only RS256 tokens with the expected issuer and a valid expiration.
 */
public final class TriaxisJwtDecoders {

	private TriaxisJwtDecoders() {
	}

	/** Decoder that downloads the signing keys from a JWKS endpoint on first use. */
	public static ReactiveJwtDecoder fromJwkSetUri(String jwkSetUri, String issuer) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decoder.setJwtValidator(validator(issuer));
		return decoder;
	}

	/** Decoder bound to a single public key (used in tests and for fixed keys). */
	public static ReactiveJwtDecoder fromPublicKey(RSAPublicKey publicKey, String issuer) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey)
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decoder.setJwtValidator(validator(issuer));
		return decoder;
	}

	private static OAuth2TokenValidator<Jwt> validator(String issuer) {
		return JwtValidators.createDefaultWithIssuer(issuer);
	}

}
