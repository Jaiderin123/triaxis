package dev.triaxis.triage.infrastructure.adapter.in.scheduler;

import dev.triaxis.triage.application.port.in.EscalateOverduePatientsUseCase;
import dev.triaxis.triage.infrastructure.config.EscalationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Escalates overdue patients periodically. Cycles never overlap ({@code concatMap}), a full batch triggers
 * another batch at once, and a failed cycle is logged and retried on the next tick instead of stopping the loop.
 * Several replicas may run it: the statement skips rows locked by another one.
 */
@Component
@ConditionalOnProperty(prefix = "triage.escalation", name = "enabled", havingValue = "true", matchIfMissing = true)
class EscalationScheduler implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

	private final EscalateOverduePatientsUseCase escalation;
	private final EscalationProperties properties;
	private Disposable subscription;

	EscalationScheduler(EscalateOverduePatientsUseCase escalation, EscalationProperties properties) {
		this.escalation = escalation;
		this.properties = properties;
	}

	@Override
	public void start() {
		subscription = Flux.interval(properties.interval())
				.onBackpressureDrop()
				.concatMap(tick -> runCycle())
				.subscribe(unused -> { }, error -> log.error("The escalation loop stopped", error));
		log.info("Escalation started (every {}, batch of {})", properties.interval(), escalation.batchSize());
	}

	/** Runs batches until one is not full, and returns how many patients were escalated in the cycle. */
	Mono<Integer> runCycle() {
		return batch()
				.expand(count -> count >= escalation.batchSize() ? batch() : Mono.empty())
				.reduce(0, Integer::sum)
				.doOnNext(total -> {
					if (total > 0) {
						log.info("Escalated {} patient(s) that exceeded their SLA", total);
					}
				})
				.onErrorResume(error -> {
					log.warn("Escalation cycle failed, it will be retried on the next tick", error);
					return Mono.just(0);
				});
	}

	/** One batch with its own time limit, so a stuck database call never stops the loop. */
	private Mono<Integer> batch() {
		return escalation.escalateOverdue().timeout(properties.batchTimeout());
	}

	@Override
	public void stop() {
		if (subscription != null) {
			subscription.dispose();
			subscription = null;
		}
	}

	@Override
	public boolean isRunning() {
		return subscription != null && !subscription.isDisposed();
	}

}
