package dev.triaxis.web;

import java.util.Map;

import dev.triaxis.web.problem.AbstractProblemExceptionHandler;
import dev.triaxis.web.problem.ProblemWriter;
import dev.triaxis.web.problem.Problems;
import dev.triaxis.web.security.ProblemAccessDeniedHandler;
import dev.triaxis.web.security.ProblemAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Minimal application that exercises the shared web support. */
@SpringBootApplication
public class WebTestApp {

	public record Payload(Integer edad, String nombre) {
	}

	public static class SampleDomainException extends RuntimeException {

		public SampleDomainException() {
			super("sample domain failure");
		}

	}

	@Component
	@Order(-2)
	static class SampleHandler extends AbstractProblemExceptionHandler {

		SampleHandler(ProblemWriter writer) {
			super(writer);
		}

		@Override
		protected ProblemDetail mapDomain(Throwable error) {
			return error instanceof SampleDomainException ? Problems.of(HttpStatus.CONFLICT, error.getMessage()) : null;
		}

	}

	@Bean
	RouterFunction<ServerResponse> routes() {
		return RouterFunctions.route()
				.POST("/echo", request -> request.bodyToMono(Payload.class)
						.flatMap(body -> ServerResponse.ok().bodyValue(Map.of("ok", true))))
				.GET("/domain", request -> Mono.error(new SampleDomainException()))
				.GET("/boom", request -> Mono.error(new IllegalStateException("secret internal detail")))
				.GET("/teapot", request -> Mono.error(new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "short and stout")))
				.GET("/secured", request -> ServerResponse.ok().bodyValue(Map.of("secured", true)))
				.GET("/admin", request -> ServerResponse.ok().bodyValue(Map.of("admin", true)))
				.GET("/actuator/health/liveness", request -> ServerResponse.ok().bodyValue(Map.of("status", "UP")))
				.build();
	}

	@Bean
	@Order(0)
	SecurityWebFilterChain apiChain(ServerHttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
			ProblemAccessDeniedHandler denied) {
		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
				.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/actuator/health/**", "/echo", "/domain", "/boom", "/teapot").permitAll()
						.pathMatchers("/admin").hasRole("ADMIN")
						.anyExchange().authenticated())
				.build();
	}

	@Bean
	MapReactiveUserDetailsService users() {
		return new MapReactiveUserDetailsService(
				User.withUsername("ana").password("{noop}secret").roles("USER").build());
	}

}
