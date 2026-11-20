package dev.triaxis.assistant.application.port.in;

import dev.triaxis.assistant.domain.model.UrgencyEvent;
import reactor.core.publisher.Mono;

/** Applies one triage event to the urgency view; safe to call again with the same event. */
public interface ApplyTriageEventUseCase {

	Mono<Boolean> apply(UrgencyEvent event);

}
