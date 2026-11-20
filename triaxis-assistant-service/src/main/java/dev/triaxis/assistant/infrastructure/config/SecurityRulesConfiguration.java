package dev.triaxis.assistant.infrastructure.config;

import dev.triaxis.security.AuthorizationRulesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Role rules of the assistant endpoints; everything else just needs a valid token. */
@Configuration
class SecurityRulesConfiguration {

	@Bean
	AuthorizationRulesCustomizer assistantAuthorizationRules() {
		return exchanges -> exchanges
				.pathMatchers(HttpMethod.POST, "/asistente/consulta").hasRole("DOCTOR")
				.pathMatchers(HttpMethod.GET, "/asistente/estado-urgencias").hasAnyRole("NURSE", "DOCTOR");
	}

}
