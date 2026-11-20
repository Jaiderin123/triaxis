package dev.triaxis.web;

import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.security.MetricsProperties;
import dev.triaxis.web.security.ProblemAccessDeniedHandler;
import dev.triaxis.web.security.ProblemAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.StringUtils;

/** Security pieces shared by the services: 401 and 403 problems and the protected metrics endpoint. */
@AutoConfiguration(before = ReactiveSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(SecurityWebFilterChain.class)
@EnableConfigurationProperties(MetricsProperties.class)
public class TriaxisWebSecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemWriter writer) {
		return new ProblemAuthenticationEntryPoint(writer);
	}

	@Bean
	@ConditionalOnMissingBean
	ProblemAccessDeniedHandler problemAccessDeniedHandler(ProblemWriter writer) {
		return new ProblemAccessDeniedHandler(writer);
	}

	/**
	 * {@code /actuator/prometheus} needs HTTP Basic with the credentials of the environment. Without a
	 * configured password the endpoint is denied to everybody. It is not routed outside the cluster either.
	 */
	@Bean
	@Order(-100)
	SecurityWebFilterChain metricsSecurityWebFilterChain(ServerHttpSecurity http, MetricsProperties properties,
			ProblemWriter writer, ProblemAccessDeniedHandler denied) {
		ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(writer,
				"Basic realm=\"metrics\"");
		http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/prometheus"))
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
		if (StringUtils.hasText(properties.password())) {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			ReactiveAuthenticationManager manager = new UserDetailsRepositoryReactiveAuthenticationManager(
					new MapReactiveUserDetailsService(User.withUsername(properties.username())
							.password(encoder.encode(properties.password())).roles("METRICS").build()));
			((UserDetailsRepositoryReactiveAuthenticationManager) manager).setPasswordEncoder(encoder);
			http.httpBasic(basic -> basic.authenticationManager(manager).authenticationEntryPoint(entryPoint))
					.authorizeExchange(exchanges -> exchanges.anyExchange().hasRole("METRICS"));
		}
		else {
			http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
					.authorizeExchange(exchanges -> exchanges.anyExchange().denyAll());
		}
		return http.build();
	}

}
