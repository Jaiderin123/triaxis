package dev.triaxis.triage.application.port.out;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface IdGenerator {

	/** Random identifiers read the system entropy source, which can block, so the adapter must not do it on an event-loop thread. */
	Mono<UUID> next();

}
