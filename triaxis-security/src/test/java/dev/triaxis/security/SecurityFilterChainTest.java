package dev.triaxis.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = SecurityFilterChainTest.TestApp.class, properties = {
		"triaxis.security.jwks-uri=http://localhost:0/.well-known/jwks.json"
})
@AutoConfigureWebTestClient
class SecurityFilterChainTest {

	@SpringBootApplication
	@Import(Routes.class)
	static class TestApp {
	}

	@Configuration
	static class Routes {

		@Bean
		RouterFunction<ServerResponse> routes() {
			return RouterFunctions.route()
					.GET("/actuator/health/liveness", request -> ServerResponse.ok().bodyValue("UP"))
					.GET("/nurse-only", request -> ServerResponse.ok().bodyValue("nurse"))
					.GET("/anyone", request -> ServerResponse.ok().bodyValue("any"))
					.build();
		}

		@Bean
		AuthorizationRulesCustomizer rules() {
			return exchanges -> exchanges.pathMatchers("/nurse-only").hasRole("NURSE");
		}

	}

	@Autowired
	WebTestClient client;

	@Test
	void healthProbeIsPublic() {
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
	}

	@Test
	void protectedPathWithoutTokenIsUnauthorizedInTheStandardFormat() {
		client.get().uri("/anyone").exchange()
				.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
				.expectHeader().contentTypeCompatibleWith("application/problem+json")
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.instance").isEqualTo("/anyone")
				.jsonPath("$.detail").isEqualTo("Authentication is required")
				.jsonPath("$.timestamp").isNotEmpty();
	}

	@Test
	void anInvalidTokenIsUnauthorizedWithAnInvalidTokenChallenge() {
		client.get().uri("/anyone").header("Authorization", "Bearer not.a.token").exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"")
				.expectBody().jsonPath("$.status").isEqualTo(401);
	}

	@Test
	void authenticatedUserReachesGenericPath() {
		client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
				.get().uri("/anyone").exchange().expectStatus().isOk();
	}

	@Test
	void roleRuleAllowsMatchingRole() {
		client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_NURSE")))
				.get().uri("/nurse-only").exchange().expectStatus().isOk();
	}

	@Test
	void roleRuleRejectsOtherRole() {
		client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
				.get().uri("/nurse-only").exchange().expectStatus().isForbidden()
				.expectHeader().contentTypeCompatibleWith("application/problem+json")
				.expectBody().jsonPath("$.status").isEqualTo(403).jsonPath("$.detail").isEqualTo("Access denied");
	}

}
