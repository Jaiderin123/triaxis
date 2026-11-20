package dev.triaxis.assistant.infrastructure.adapter.in.web.handler;

import dev.triaxis.assistant.application.port.in.AskClinicalQuestionUseCase;
import dev.triaxis.assistant.infrastructure.adapter.in.web.dto.ConsultaRequest;
import dev.triaxis.assistant.infrastructure.adapter.in.web.mapper.AnswerWebMapper;
import dev.triaxis.assistant.infrastructure.adapter.in.web.validation.InvalidRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Translates HTTP to the question use case and back; the rules live in the domain and the use case. */
@Component
public class ConsultaHandler {

	private final AskClinicalQuestionUseCase assistant;
	private final AnswerWebMapper mapper;

	ConsultaHandler(AskClinicalQuestionUseCase assistant, AnswerWebMapper mapper) {
		this.assistant = assistant;
		this.mapper = mapper;
	}

	public Mono<ServerResponse> ask(ServerRequest request) {
		return request.bodyToMono(ConsultaRequest.class)
				.switchIfEmpty(Mono.error(() -> new InvalidRequestException("cuerpo", "is required")))
				.flatMap(body -> assistant.ask(body.pregunta()))
				.flatMap(answer -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
						.bodyValue(mapper.toResponse(answer)));
	}

}
