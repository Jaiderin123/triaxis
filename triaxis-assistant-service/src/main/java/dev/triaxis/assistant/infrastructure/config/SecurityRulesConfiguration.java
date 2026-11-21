package dev.triaxis.assistant.infrastructure.config;

import dev.triaxis.security.AuthorizationRulesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Role rules of the assistant endpoints; any other route is denied (deny by default, ADR 0022). */
@Configuration
class SecurityRulesConfiguration {

	@Bean
	AuthorizationRulesCustomizer assistantAuthorizationRules() {
		return exchanges -> exchanges
				.pathMatchers(HttpMethod.POST, "/asistente/consulta").hasRole("DOCTOR")
				.pathMatchers(HttpMethod.GET, "/asistente/estado-urgencias").hasAnyRole("NURSE", "DOCTOR");
	}

}
