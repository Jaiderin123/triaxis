package dev.triaxis.auth;

import java.util.List;
import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import dev.triaxis.security.TriaxisJwtDecoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class TokenEndpointTest {

	@Autowired
	WebTestClient client;

	@LocalServerPort
	int port;

	@Test
	void issuesTokenForValidCredentials() {
		Map<String, Object> body = login("nurse", "nurse-demo").expectStatus().isOk()
				.expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
				}).returnResult().getResponseBody();

		assertThat(body).containsKeys("access_token", "token_type", "expires_in");
		assertThat(body.get("token_type")).isEqualTo("Bearer");
		assertThat(((Number) body.get("expires_in")).longValue()).isEqualTo(900L);
	}

	@Test
	void rejectsWrongPasswordAndUnknownUserWithTheSameAnswer() {
		String wrongPassword = login("nurse", "wrong").expectStatus().isUnauthorized()
				.expectBody(String.class).returnResult().getResponseBody();
		String unknownUser = login("ghost", "wrong").expectStatus().isUnauthorized()
				.expectBody(String.class).returnResult().getResponseBody();

		assertThat(wrongPassword).isEqualTo(unknownUser);
		assertThat(wrongPassword).contains("Invalid credentials");
	}

	@Test
	void namesTheInvalidFieldWhenBodyIsIncomplete() {
		client.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("username", "nurse", "password", ""))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("password"));
	}

	@Test
	void rejectsMalformedBody() {
		client.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{not json")
				.exchange().expectStatus().isBadRequest();
	}

	@Test
	void jwksExposesOnlyThePublicKey() throws Exception {
		String json = client.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk()
				.expectBody(String.class).returnResult().getResponseBody();

		JWKSet set = JWKSet.parse(json);
		assertThat(set.getKeys()).hasSize(1);
		RSAKey key = set.getKeys().get(0).toRSAKey();
		assertThat(key.isPrivate()).isFalse();
		assertThat(json).doesNotContain("\"d\"");
	}

	@Test
	void tokenIssuedByAuthIsValidatedWithTheSharedDecoder() {
		String token = (String) login("doctor", "doctor-demo").expectStatus().isOk()
				.expectBody(Map.class).returnResult().getResponseBody().get("access_token");

		ReactiveJwtDecoder decoder = TriaxisJwtDecoders
				.fromJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json", "triaxis-auth");
		Jwt jwt = decoder.decode(token).block();

		assertThat(jwt).isNotNull();
		assertThat(jwt.getSubject()).isEqualTo("doctor");
		assertThat(jwt.<List<String>>getClaim("roles")).containsExactly("DOCTOR");
		assertThat(jwt.getId()).isNotBlank();
		assertThat(jwt.getHeaders()).containsKey("kid");
		assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
	}

	@Test
	void otherPathsAreNotPublic() {
		client.get().uri("/anything").exchange().expectStatus().value(status -> assertThat(status)
				.isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
	}

	@Test
	void healthProbesArePublic() {
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
		client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
	}

	private WebTestClient.ResponseSpec login(String username, String password) {
		return client.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("username", username, "password", password))
				.exchange();
	}

}
