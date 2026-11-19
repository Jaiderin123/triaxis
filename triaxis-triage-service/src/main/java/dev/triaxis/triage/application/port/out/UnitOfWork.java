package dev.triaxis.triage.application.port.out;

import reactor.core.publisher.Mono;

/** Runs the given work in one transaction: everything is committed, or nothing is. */
public interface UnitOfWork {

	<T> Mono<T> execute(Mono<T> work);

}
