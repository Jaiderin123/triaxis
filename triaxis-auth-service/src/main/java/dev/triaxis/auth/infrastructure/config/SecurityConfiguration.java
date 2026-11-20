package dev.triaxis.auth.infrastructure.config;

import dev.triaxis.web.security.ProblemAccessDeniedHandler;
import dev.triaxis.web.security.ProblemAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/** Only the token endpoint, the JWKS and the health probes are public; everything else is denied. */
@Configuration
class SecurityConfiguration {

	@Bean
	SecurityWebFilterChain authSecurityWebFilterChain(ServerHttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
			ProblemAccessDeniedHandler denied) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(HttpMethod.POST, "/auth/token").permitAll()
						.pathMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
						.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.anyExchange().denyAll())
				.build();
	}

}
