package dev.triaxis.triage.infrastructure.adapter.out.id;

import java.util.UUID;

import dev.triaxis.triage.application.port.out.IdGenerator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Random UUIDs come from the system entropy source (a blocking read), so they are produced off the event loop. */
@Component
class UuidIdGenerator implements IdGenerator {

	@Override
	public Mono<UUID> next() {
		return Mono.fromCallable(UUID::randomUUID).subscribeOn(Schedulers.boundedElastic());
	}

}
