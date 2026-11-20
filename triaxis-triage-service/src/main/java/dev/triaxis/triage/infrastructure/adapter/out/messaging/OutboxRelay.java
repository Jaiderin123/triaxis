package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.infrastructure.config.OutboxProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Delivers the events of the outbox to Kafka. Each cycle runs in one transaction that first takes a PostgreSQL
 * advisory lock (only one relay works at a time, which keeps the order per patient), then reads the oldest
 * pending rows with {@code FOR UPDATE SKIP LOCKED} and, one by one and in order, sends them and marks them as
 * published. A failed send stops the cycle: the event stays pending and is retried. Delivery is at least once.
 */
@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private static final String LOCK = "SELECT pg_try_advisory_xact_lock(hashtext('triaxis.outbox.relay')) AS acquired";

	private static final String PENDING = """
			SELECT id, message_key, event_type, correlation_id, traceparent, payload::text AS payload
			FROM outbox_events
			WHERE published_at IS NULL
			ORDER BY id
			LIMIT :batch
			FOR UPDATE SKIP LOCKED
			""";

	private static final String MARK_PUBLISHED = "UPDATE outbox_events SET published_at = :now WHERE id = :id";

	private static final String COUNT_PENDING = "SELECT count(*) AS total FROM outbox_events WHERE published_at IS NULL";

	private final DatabaseClient client;
	private final TransactionalOperator transaction;
	private final KafkaTemplate<String, String> kafka;
	private final OutboxProperties.Relay settings;
	private final AtomicLong pending = new AtomicLong();
	private final Counter published;
	private final Counter failures;

	OutboxRelay(DatabaseClient client, TransactionalOperator transaction, KafkaTemplate<String, String> kafka,
			OutboxProperties properties, MeterRegistry registry) {
		this.client = client;
		this.transaction = transaction;
		this.kafka = kafka;
		this.settings = properties.relay();
		this.published = registry.counter("triage.outbox.published");
		this.failures = registry.counter("triage.outbox.failures");
		Gauge.builder("triage.outbox.pending", pending, AtomicLong::get).register(registry);
	}

	/** The number of events sent in a cycle that can take as many as one batch. */
	public int batchSize() {
		return settings.batchSize();
	}

	/**
	 * Runs one delivery cycle and returns how many events were published. It never fails: problems are
	 * logged, counted and retried in the next cycle.
	 */
	public Mono<Integer> relayOnce() {
		return transaction.transactional(client.sql(LOCK).map(row -> row.get("acquired", Boolean.class)).one()
						.flatMap(acquired -> Boolean.TRUE.equals(acquired) ? deliverBatch() : Mono.just(0)))
				// A stuck cycle is cancelled and rolled back; events sent in it stay pending and are sent again.
				.timeout(settings.cycleTimeout())
				.onErrorResume(error -> {
					failures.increment();
					log.warn("Outbox relay cycle failed, it will be retried: {}", error.toString());
					return Mono.just(0);
				})
				.flatMap(count -> refreshPending().thenReturn(count));
	}

	private Mono<Integer> deliverBatch() {
		long deadline = System.nanoTime() + settings.cycleBudget().toNanos();
		java.util.concurrent.atomic.AtomicInteger started = new java.util.concurrent.atomic.AtomicInteger();
		return client.sql(PENDING).bind("batch", settings.batchSize())
				.map((row, metadata) -> new PendingEvent(row.get("id", Long.class), row.get("message_key", String.class),
						row.get("event_type", String.class), row.get("correlation_id", String.class),
						row.get("traceparent", String.class), row.get("payload", String.class)))
				.all().collectList()
				.flatMapMany(Flux::fromIterable)
				// Past the budget, stop between two events and commit what was sent; the first one always goes.
				.concatMap(event -> started.getAndIncrement() > 0 && System.nanoTime() > deadline ? Mono.just(false)
						: deliver(event))
				.takeWhile(Boolean::booleanValue)
				.count()
				.map(Long::intValue);
	}

	private Mono<Boolean> deliver(PendingEvent event) {
		return send(event)
				.then(client.sql(MARK_PUBLISHED).bind("now", OffsetDateTime.now(ZoneOffset.UTC)).bind("id", event.id())
						.fetch().rowsUpdated())
				.doOnSuccess(updated -> published.increment())
				.thenReturn(true)
				.onErrorResume(error -> {
					failures.increment();
					log.warn("Could not deliver outbox event {} ({}): {}", event.id(), event.type(), error.toString());
					return Mono.just(false);
				});
	}

	/** The Kafka client may block while fetching metadata, so the send runs off the event loop and has a timeout. */
	private Mono<Void> send(PendingEvent event) {
		return Mono.defer(() -> {
					ProducerRecord<String, String> record = new ProducerRecord<>(TriageEvent.TOPIC, event.key(),
							event.payload());
					record.headers().add(TriageEvent.EVENT_TYPE_HEADER, event.type().getBytes(StandardCharsets.UTF_8));
					if (event.correlationId() != null) {
						record.headers().add(TriageEvent.CORRELATION_ID_HEADER,
								event.correlationId().getBytes(StandardCharsets.UTF_8));
					}
					if (event.traceparent() != null) {
						record.headers().add(TriageEvent.TRACEPARENT_HEADER,
								event.traceparent().getBytes(StandardCharsets.UTF_8));
					}
					return Mono.fromFuture(kafka.send(record));
				})
				.subscribeOn(Schedulers.boundedElastic())
				.timeout(settings.sendTimeout())
				.then();
	}

	private Mono<Void> refreshPending() {
		return client.sql(COUNT_PENDING).map(row -> row.get("total", Long.class)).one()
				.doOnNext(pending::set)
				.timeout(settings.sendTimeout())
				.onErrorResume(error -> {
					log.debug("Could not refresh the pending outbox gauge: {}", error.toString());
					return Mono.empty();
				})
				.then();
	}

	private record PendingEvent(Long id, String key, String type, String correlationId, String traceparent,
			String payload) {
	}

}
