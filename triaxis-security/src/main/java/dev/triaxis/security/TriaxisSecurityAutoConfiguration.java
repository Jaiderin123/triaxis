package dev.triaxis.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import dev.triaxis.web.security.ProblemAccessDeniedHandler;
import dev.triaxis.web.security.ProblemAuthenticationEntryPoint;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

/**
 * Shared resource-server configuration: validates RS256 tokens issued by the auth service,
 * maps the {@code roles} claim to {@code ROLE_*} authorities and keeps the health probes public.
 */
@AutoConfiguration(after = dev.triaxis.web.TriaxisWebSecurityAutoConfiguration.class,
		before = {ReactiveSecurityAutoConfiguration.class, ReactiveOAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "triaxis.security", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(TriaxisSecurityProperties.class)
public class TriaxisSecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ReactiveJwtDecoder.class)
	ReactiveJwtDecoder triaxisJwtDecoder(TriaxisSecurityProperties properties) {
		Assert.hasText(properties.jwksUri(), "triaxis.security.jwks-uri must be set");
		return TriaxisJwtDecoders.fromJwkSetUri(properties.jwksUri(), properties.issuer(), properties.audience(),
				properties.jwks().settings());
	}

	@Bean
	@ConditionalOnMissingBean(name = "triaxisJwtAuthenticationConverter")
	Converter<Jwt, Mono<AbstractAuthenticationToken>> triaxisJwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(authorities));
		return converter;
	}

	@Bean
	SecurityWebFilterChain triaxisSecurityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
			Converter<Jwt, Mono<AbstractAuthenticationToken>> triaxisJwtAuthenticationConverter,
			ObjectProvider<AuthorizationRulesCustomizer> customizers, ProblemAuthenticationEntryPoint entryPoint,
			ProblemAccessDeniedHandler denied) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.authorizeExchange(exchanges -> {
					exchanges.pathMatchers("/actuator/health", "/actuator/health/**").permitAll();
					customizers.orderedStream().forEach(customizer -> customizer.customize(exchanges));
					exchanges.pathMatchers("/actuator/info").authenticated();
					// Deny by default: a route without an explicit rule is refused, even with a valid token.
					exchanges.anyExchange().denyAll();
				})
				.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(denied)
						.jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(triaxisJwtAuthenticationConverter)))
				.build();
	}

}
