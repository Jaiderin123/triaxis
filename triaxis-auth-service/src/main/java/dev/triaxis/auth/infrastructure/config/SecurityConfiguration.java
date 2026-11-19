package dev.triaxis.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/** Only the token endpoint, the JWKS and the health probes are public; everything else is denied. */
@Configuration
class SecurityConfiguration {

	@Bean
	SecurityWebFilterChain authSecurityWebFilterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(HttpMethod.POST, "/auth/token").permitAll()
						.pathMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
						.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.anyExchange().denyAll())
				.build();
	}

}
