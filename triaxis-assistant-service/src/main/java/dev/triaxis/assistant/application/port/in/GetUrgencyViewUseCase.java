package dev.triaxis.assistant.application.port.in;

import dev.triaxis.assistant.domain.model.UrgencyView;
import reactor.core.publisher.Mono;

/** RF-04 B: active patients per level, built only from the events of the topic. */
public interface GetUrgencyViewUseCase {

	Mono<UrgencyView> get();

}
