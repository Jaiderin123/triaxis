package dev.triaxis.assistant.infrastructure.adapter.in.web.handler;

import dev.triaxis.assistant.application.port.in.AskClinicalQuestionUseCase;
import dev.triaxis.assistant.infrastructure.adapter.in.web.dto.ConsultaRequest;
import java.security.Principal;

import dev.triaxis.assistant.infrastructure.adapter.in.web.mapper.AnswerWebMapper;
import dev.triaxis.assistant.infrastructure.adapter.in.web.ratelimit.QuestionRateLimiter;
import dev.triaxis.assistant.infrastructure.adapter.in.web.validation.InvalidRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Translates HTTP to the question use case and back; the rules live in the domain and the use case. The limit of
 * questions per user is checked first, before the body is read (ADR 0025).
 */
@Component
public class ConsultaHandler {

	private final AskClinicalQuestionUseCase assistant;
	private final AnswerWebMapper mapper;
	private final QuestionRateLimiter limiter;

	ConsultaHandler(AskClinicalQuestionUseCase assistant, AnswerWebMapper mapper, QuestionRateLimiter limiter) {
		this.assistant = assistant;
		this.mapper = mapper;
		this.limiter = limiter;
	}

	public Mono<ServerResponse> ask(ServerRequest request) {
		return request.principal().map(Principal::getName).flatMap(limiter::check)
				.then(request.bodyToMono(ConsultaRequest.class))
				.switchIfEmpty(Mono.error(() -> new InvalidRequestException("cuerpo", "is required")))
				.flatMap(body -> assistant.ask(body.pregunta()))
				.flatMap(answer -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
						.bodyValue(mapper.toResponse(answer)));
	}

}
