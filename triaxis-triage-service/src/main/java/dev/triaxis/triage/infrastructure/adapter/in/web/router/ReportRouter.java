package dev.triaxis.triage.infrastructure.adapter.in.web.router;

import dev.triaxis.triage.infrastructure.adapter.in.web.handler.ReportHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
class ReportRouter {

	@Bean
	RouterFunction<ServerResponse> reportRoutes(ReportHandler handler) {
		return RouterFunctions.route().GET("/triage/reportes/operacion", handler::operation).build();
	}

}
