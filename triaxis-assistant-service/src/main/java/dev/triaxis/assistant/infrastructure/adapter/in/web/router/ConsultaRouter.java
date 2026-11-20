package dev.triaxis.assistant.infrastructure.adapter.in.web.router;

import dev.triaxis.assistant.infrastructure.adapter.in.web.handler.ConsultaHandler;
import dev.triaxis.assistant.infrastructure.adapter.in.web.handler.UrgenciasHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
class ConsultaRouter {

	@Bean
	RouterFunction<ServerResponse> consultaRoutes(ConsultaHandler handler) {
		return RouterFunctions.route().POST("/asistente/consulta", handler::ask).build();
	}

	@Bean
	RouterFunction<ServerResponse> urgenciasRoutes(UrgenciasHandler handler) {
		return RouterFunctions.route().GET("/asistente/estado-urgencias", handler::get).build();
	}

}
