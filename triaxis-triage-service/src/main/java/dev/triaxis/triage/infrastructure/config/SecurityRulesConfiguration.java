package dev.triaxis.triage.infrastructure.config;

import dev.triaxis.security.AuthorizationRulesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Role rules of the triage endpoints (see ADR 0013); everything else just needs a valid token. */
@Configuration
class SecurityRulesConfiguration {

	@Bean
	AuthorizationRulesCustomizer triageAuthorizationRules() {
		return exchanges -> exchanges
				.pathMatchers(HttpMethod.POST, "/triage/pacientes").hasRole("NURSE")
				.pathMatchers(HttpMethod.PATCH, "/triage/pacientes/*/estado").hasAnyRole("NURSE", "DOCTOR")
				.pathMatchers(HttpMethod.GET, "/triage/pacientes/*").hasAnyRole("NURSE", "DOCTOR");
	}

}
