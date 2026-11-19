package dev.triaxis.triage.infrastructure.adapter.in.scheduler;

import dev.triaxis.triage.infrastructure.adapter.out.messaging.OutboxRelay;
import dev.triaxis.triage.infrastructure.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Runs the outbox relay periodically. Cycles never overlap ({@code concatMap}) and a full batch triggers
 * another cycle immediately instead of waiting for the next tick.
 */
@Component
@ConditionalOnProperty(prefix = "triage.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

	private final OutboxRelay relay;
	private final OutboxProperties properties;
	private Disposable subscription;

	OutboxRelayScheduler(OutboxRelay relay, OutboxProperties properties) {
		this.relay = relay;
		this.properties = properties;
	}

	@Override
	public void start() {
		subscription = Flux.interval(properties.relay().interval())
				.onBackpressureDrop()
				.concatMap(tick -> relay.relayOnce()
						.expand(count -> count >= relay.batchSize() ? relay.relayOnce() : reactor.core.publisher.Mono.empty())
						.then())
				.subscribe(unused -> { }, error -> log.error("The outbox relay stopped", error));
		log.info("Outbox relay started (every {}, batch of {})", properties.relay().interval(),
				properties.relay().batchSize());
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
