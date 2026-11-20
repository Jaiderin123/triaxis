package dev.triaxis.auth;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = "auth.rate-limit.max-attempts=3")
@AutoConfigureWebTestClient
@ActiveProfiles("local")
class RateLimitEndpointTest {

	@Autowired
	WebTestClient client;

	private WebTestClient.ResponseSpec login(String username, String password) {
		return client.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("username", username, "password", password)).exchange();
	}

	@Test
	void guessingPasswordsIsSlowedDown() {
		for (int i = 0; i < 3; i++) {
			login("doctor", "wrong-" + i).expectStatus().isUnauthorized();
		}

		login("doctor", "doctor-demo").expectStatus().isEqualTo(429)
				.expectHeader().exists("Retry-After")
				.expectBody()
				.jsonPath("$.status").isEqualTo(429)
				.jsonPath("$.detail").isEqualTo("Too many attempts, try again later")
				.jsonPath("$.traceId").isNotEmpty();
	}

	@Test
	void otherUsersAreNotAffected() {
		for (int i = 0; i < 4; i++) {
			login("someone-else", "wrong");
		}

		login("nurse", "nurse-demo").expectStatus().isOk();
	}

}
