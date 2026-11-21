package dev.triaxis.triage.infrastructure.config;

import dev.triaxis.security.AuthorizationRulesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Role rules of the triage endpoints (see ADR 0013); any other route is denied (deny by default, ADR 0022). */
@Configuration
class SecurityRulesConfiguration {

	@Bean
	AuthorizationRulesCustomizer triageAuthorizationRules() {
		return exchanges -> exchanges
				.pathMatchers(HttpMethod.POST, "/triage/pacientes").hasRole("NURSE")
				.pathMatchers(HttpMethod.PATCH, "/triage/pacientes/*/estado").hasAnyRole("NURSE", "DOCTOR")
				.pathMatchers(HttpMethod.GET, "/triage/pacientes/*").hasAnyRole("NURSE", "DOCTOR")
				.pathMatchers(HttpMethod.GET, "/triage/reportes/operacion").hasRole("DOCTOR");
	}

}
