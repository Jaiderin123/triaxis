package dev.triaxis.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

class TriaxisJwtDecodersTest {

	private static final String ISSUER = "triaxis-auth";

	private static KeyPair keyPair;
	private static ReactiveJwtDecoder decoder;

	@BeforeAll
	static void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keyPair = generator.generateKeyPair();
		decoder = TriaxisJwtDecoders.fromPublicKey((RSAPublicKey) keyPair.getPublic(), ISSUER);
	}

	@Test
	void acceptsValidToken() throws Exception {
		String token = sign(keyPair, ISSUER, Instant.now().plusSeconds(600));

		StepVerifier.create(decoder.decode(token))
				.assertNext(jwt -> {
					org.assertj.core.api.Assertions.assertThat(jwt.getSubject()).isEqualTo("nurse");
					org.assertj.core.api.Assertions.assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("NURSE");
				})
				.verifyComplete();
	}

	@Test
	void rejectsExpiredToken() throws Exception {
		String token = sign(keyPair, ISSUER, Instant.now().minusSeconds(3600));

		StepVerifier.create(decoder.decode(token)).expectError().verify();
	}

	@Test
	void rejectsWrongIssuer() throws Exception {
		String token = sign(keyPair, "someone-else", Instant.now().plusSeconds(600));

		StepVerifier.create(decoder.decode(token)).expectError().verify();
	}

	@Test
	void rejectsTokenSignedWithAnotherKey() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		String token = sign(generator.generateKeyPair(), ISSUER, Instant.now().plusSeconds(600));

		StepVerifier.create(decoder.decode(token)).expectError().verify();
	}

	@Test
	void rejectsTamperedPayload() throws Exception {
		String token = sign(keyPair, ISSUER, Instant.now().plusSeconds(600));
		String[] parts = token.split("\\.");
		String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"admin\",\"roles\":[\"DOCTOR\"],\"iss\":\"" + ISSUER + "\",\"exp\":"
						+ Instant.now().plusSeconds(600).getEpochSecond() + "}").getBytes());

		StepVerifier.create(decoder.decode(parts[0] + "." + forgedPayload + "." + parts[2])).expectError().verify();
	}

	private static String sign(KeyPair pair, String issuer, Instant expiresAt) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject("nurse")
				.issuer(issuer)
				.issueTime(new Date())
				.expirationTime(Date.from(expiresAt))
				.claim("roles", List.of("NURSE"))
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
		return jwt.serialize();
	}

}
