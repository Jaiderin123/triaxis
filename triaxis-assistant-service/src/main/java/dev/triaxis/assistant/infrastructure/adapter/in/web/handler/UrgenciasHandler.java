package dev.triaxis.assistant.infrastructure.adapter.in.web.handler;

import dev.triaxis.assistant.application.port.in.GetUrgencyViewUseCase;
import dev.triaxis.assistant.infrastructure.adapter.in.web.mapper.UrgencyWebMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Reads the urgency view; it never calls the triage service and does not depend on the language model. */
@Component
public class UrgenciasHandler {

	private final GetUrgencyViewUseCase urgency;
	private final UrgencyWebMapper mapper;

	UrgenciasHandler(GetUrgencyViewUseCase urgency, UrgencyWebMapper mapper) {
		this.urgency = urgency;
		this.mapper = mapper;
	}

	public Mono<ServerResponse> get(ServerRequest request) {
		return urgency.get().flatMap(view -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
				.bodyValue(mapper.toResponse(view)));
	}

}
