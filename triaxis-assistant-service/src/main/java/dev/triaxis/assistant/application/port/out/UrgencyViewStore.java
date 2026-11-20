package dev.triaxis.assistant.application.port.out;

import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.assistant.domain.model.UrgencyView;
import reactor.core.publisher.Mono;

/** The read model of the urgency view. Applying the same event again changes nothing (ADR 0021). */
public interface UrgencyViewStore {

	/** Applies the event atomically; returns whether a count changed. */
	Mono<Boolean> apply(UrgencyEvent event);

	Mono<UrgencyView> read();

	/** Whether the view was built in this store; false after a new or lost store, which triggers a rebuild. */
	Mono<Boolean> isInitialized();

	/** Removes every patient and count and marks the store as initialized, before a replay of the topic. */
	Mono<Void> reset();

}
