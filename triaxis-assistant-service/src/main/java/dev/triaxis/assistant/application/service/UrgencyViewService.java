package dev.triaxis.assistant.application.service;

import dev.triaxis.assistant.application.port.in.ApplyTriageEventUseCase;
import dev.triaxis.assistant.application.port.in.GetUrgencyViewUseCase;
import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.assistant.domain.model.UrgencyView;
import reactor.core.publisher.Mono;

/** The urgency view never calls the triage service: it is the result of the events applied to the store. */
public class UrgencyViewService implements ApplyTriageEventUseCase, GetUrgencyViewUseCase {

	private final UrgencyViewStore store;

	public UrgencyViewService(UrgencyViewStore store) {
		this.store = store;
	}

	@Override
	public Mono<Boolean> apply(UrgencyEvent event) {
		return store.apply(event);
	}

	@Override
	public Mono<UrgencyView> get() {
		return store.read();
	}

}
