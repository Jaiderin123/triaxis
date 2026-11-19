package dev.triaxis.auth.infrastructure.adapter.in.web.router;

import dev.triaxis.auth.infrastructure.adapter.in.web.handler.AuthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
class AuthRouter {

	@Bean
	RouterFunction<ServerResponse> authRoutes(AuthHandler handler) {
		return RouterFunctions.route()
				.POST("/auth/token", handler::token)
				.GET("/.well-known/jwks.json", handler::jwks)
				.build();
	}

}
