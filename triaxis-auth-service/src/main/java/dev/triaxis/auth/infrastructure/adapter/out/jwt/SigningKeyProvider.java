package dev.triaxis.auth.infrastructure.adapter.out.jwt;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import dev.triaxis.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the RSA signing key. It is read from {@code JWT_PRIVATE_KEY} (PKCS#8 PEM). A key is generated
 * only when {@code auth.jwt.generate-key-if-missing} is enabled; otherwise startup fails.
 */
@Component
public class SigningKeyProvider {

	private static final Logger log = LoggerFactory.getLogger(SigningKeyProvider.class);

	private final RSAKey key;

	SigningKeyProvider(AuthProperties properties) {
		this.key = load(properties.jwt());
	}

	RSAKey signingKey() {
		return key;
	}

	/** Public part only, safe to publish. */
	public JWKSet publicKeySet() {
		return new JWKSet(key.toPublicJWK());
	}

	private static RSAKey load(AuthProperties.Jwt jwt) {
		try {
			if (jwt.privateKey() != null && !jwt.privateKey().isBlank()) {
				return fromPem(jwt.privateKey());
			}
			if (jwt.generateKeyIfMissing()) {
				log.warn("No JWT_PRIVATE_KEY configured: generating an ephemeral signing key. Do not use this outside local development.");
				KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
				generator.initialize(2048);
				KeyPair pair = generator.generateKeyPair();
				return build((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not load the JWT signing key", e);
		}
		throw new IllegalStateException(
				"JWT_PRIVATE_KEY is required (or enable auth.jwt.generate-key-if-missing for local development)");
	}

	private static RSAKey fromPem(String pem) throws Exception {
		String base64 = pem.replace("\\n", "\n")
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		KeyFactory factory = KeyFactory.getInstance("RSA");
		RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
		RSAPublicKey publicKey = (RSAPublicKey) factory
				.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
		return build(publicKey, privateKey);
	}

	private static RSAKey build(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) throws Exception {
		return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
	}

}
