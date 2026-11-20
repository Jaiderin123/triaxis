package dev.triaxis.auth.infrastructure.adapter.out.jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.jwk.RSAKey;
import dev.triaxis.auth.domain.model.IssuedToken;
import dev.triaxis.auth.domain.model.Role;
import dev.triaxis.auth.domain.model.User;
import dev.triaxis.auth.infrastructure.config.AuthProperties;
import dev.triaxis.security.TriaxisJwtDecoders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningKeyProviderTest {

	private static KeyPair pair;
	private static String pem;

	@BeforeAll
	static void generateKey() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		pair = generator.generateKeyPair();
		String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded());
		pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
	}

	@Test
	void loadsAPkcs8PemKeyAndDerivesThePublicKey() throws Exception {
		SigningKeyProvider provider = new SigningKeyProvider(properties(pem, false));

		RSAKey key = provider.signingKey();
		assertThat(key.isPrivate()).isTrue();
		assertThat(key.toRSAPublicKey().getModulus()).isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
		assertThat(key.getKeyID()).isNotBlank();
	}

	@Test
	void acceptsThePemWithEscapedNewlinesAsFoundInEnvironmentVariables() throws Exception {
		String oneLine = pem.trim().replace("\n", "\\n");

		SigningKeyProvider provider = new SigningKeyProvider(properties(oneLine, false));

		assertThat(provider.signingKey().toRSAPublicKey().getModulus())
				.isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
	}

	@Test
	void tokenSignedWithThePemKeyVerifiesAgainstThePublishedJwks() throws Exception {
		AuthProperties properties = properties(pem, false);
		SigningKeyProvider provider = new SigningKeyProvider(properties);
		NimbusTokenIssuer issuer = new NimbusTokenIssuer(provider, properties);

		IssuedToken token = issuer.issue(new User("nurse", "hash", Set.of(Role.NURSE)), Instant.now());

		RSAPublicKey published = provider.publicKeySet().getKeys().get(0).toRSAKey().toRSAPublicKey();
		Jwt jwt = TriaxisJwtDecoders.fromPublicKey(published, "triaxis-auth", "triaxis").decode(token.accessToken()).block();
		assertThat(jwt).isNotNull();
		assertThat(jwt.getSubject()).isEqualTo("nurse");
		assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("NURSE");
		assertThat(jwt.getHeaders().get("kid")).isEqualTo(provider.signingKey().getKeyID());
		assertThat(provider.publicKeySet().toString()).doesNotContain("\"d\"");
	}

	@Test
	void sameKeyGivesTheSameKeyIdAcrossRestarts() {
		String first = new SigningKeyProvider(properties(pem, false)).signingKey().getKeyID();
		String second = new SigningKeyProvider(properties(pem, false)).signingKey().getKeyID();

		assertThat(first).isEqualTo(second);
	}

	@Test
	void refusesToStartWithoutAKeyUnlessGenerationIsEnabled() {
		assertThatThrownBy(() -> new SigningKeyProvider(properties("", false)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_PRIVATE_KEY");
	}

	@Test
	void generatesAKeyOnlyWhenExplicitlyEnabled() {
		SigningKeyProvider provider = new SigningKeyProvider(properties("", true));

		assertThat(provider.signingKey().isPrivate()).isTrue();
	}

	@Test
	void rejectsAnInvalidPem() {
		assertThatThrownBy(() -> new SigningKeyProvider(properties("-----BEGIN PRIVATE KEY-----\nnot-a-key\n-----END PRIVATE KEY-----", false)))
				.isInstanceOf(IllegalStateException.class);
	}

	private static AuthProperties properties(String privateKey, boolean generate) {
		return new AuthProperties(new AuthProperties.Jwt("triaxis-auth", "triaxis", Duration.ofMinutes(15), privateKey, generate),
				List.of(), new AuthProperties.RateLimit(10, Duration.ofMinutes(1)));
	}

}
