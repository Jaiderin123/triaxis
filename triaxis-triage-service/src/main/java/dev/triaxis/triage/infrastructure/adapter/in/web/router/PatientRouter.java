package dev.triaxis.triage.infrastructure.adapter.in.web.router;

import dev.triaxis.triage.infrastructure.adapter.in.web.handler.PatientHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
class PatientRouter {

	@Bean
	RouterFunction<ServerResponse> patientRoutes(PatientHandler handler) {
		return RouterFunctions.route()
				.POST("/triage/pacientes", handler::register)
				.PATCH("/triage/pacientes/{id}/estado", handler::changeStatus)
				.GET("/triage/pacientes/{id}", handler::get)
				.build();
	}

}
