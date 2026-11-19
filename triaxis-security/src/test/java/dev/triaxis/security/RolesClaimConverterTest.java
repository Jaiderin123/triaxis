package dev.triaxis.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RolesClaimConverterTest {

	@Test
	void mapsRolesClaimToRoleAuthorities() {
		TriaxisSecurityAutoConfiguration configuration = new TriaxisSecurityAutoConfiguration();
		Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"),
				Map.of("sub", "doctor", "roles", List.of("DOCTOR", "NURSE")));

		StepVerifier.create(configuration.triaxisJwtAuthenticationConverter().convert(jwt))
				.assertNext((AbstractAuthenticationToken token) -> assertThat(token.getAuthorities())
						.extracting(GrantedAuthority::getAuthority)
						.containsExactlyInAnyOrder("ROLE_DOCTOR", "ROLE_NURSE"))
				.verifyComplete();
	}

}
