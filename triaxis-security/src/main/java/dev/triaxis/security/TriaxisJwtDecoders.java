package dev.triaxis.security;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Builds the decoders that accept only RS256 tokens with the expected issuer and audience and a valid expiration.
 */
public final class TriaxisJwtDecoders {

	private TriaxisJwtDecoders() {
	}

	/** Decoder that downloads the signing keys from a JWKS endpoint on first use. */
	public static ReactiveJwtDecoder fromJwkSetUri(String jwkSetUri, String issuer, String audience) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decoder.setJwtValidator(validator(issuer, audience));
		return decoder;
	}

	/** Decoder bound to a single public key (used in tests and for fixed keys). */
	public static ReactiveJwtDecoder fromPublicKey(RSAPublicKey publicKey, String issuer, String audience) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey)
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decoder.setJwtValidator(validator(issuer, audience));
		return decoder;
	}

	private static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
		OAuth2TokenValidator<Jwt> audienceValidator = token -> {
			List<String> audiences = token.getAudience();
			return audiences != null && audiences.contains(audience) ? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(
							new OAuth2Error("invalid_token", "The token is not meant for this system", null));
		};
		return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
	}

}
