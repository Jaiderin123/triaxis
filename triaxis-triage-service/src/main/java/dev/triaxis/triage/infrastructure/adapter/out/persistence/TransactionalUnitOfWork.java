package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import dev.triaxis.triage.application.port.out.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Runs the work in one R2DBC transaction; it rolls back when the work fails. */
@Component
class TransactionalUnitOfWork implements UnitOfWork {

	private final TransactionalOperator operator;

	TransactionalUnitOfWork(TransactionalOperator operator) {
		this.operator = operator;
	}

	@Override
	public <T> Mono<T> execute(Mono<T> work) {
		return operator.transactional(work);
	}

}
