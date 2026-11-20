package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.domain.model.PatientEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Writes the event to the {@code outbox_events} table. It runs inside the transaction of the change that
 * caused the event, so the event exists if and only if the change was committed. A relay delivers it to Kafka.
 */
@Component
class OutboxEventPublisher implements EventPublisher {

	private static final String INSERT = """
			INSERT INTO outbox_events (event_id, message_key, event_type, correlation_id, traceparent, payload, created_at)
			VALUES (:eventId, :messageKey, :eventType, :correlationId, :traceparent, :payload, :createdAt)
			""";

	private final DatabaseClient client;
	private final EventContractMapper mapper;
	private final ObjectMapper json;
	private final ObjectProvider<Tracer> tracer;
	private final ObjectProvider<Propagator> propagator;

	OutboxEventPublisher(DatabaseClient client, EventContractMapper mapper, ObjectMapper json,
			ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
		this.client = client;
		this.mapper = mapper;
		this.json = json;
		this.tracer = tracer;
		this.propagator = propagator;
	}

	@Override
	public Mono<Void> publish(PatientEvent event) {
		return Mono.defer(() -> {
			TriageEvent contract = mapper.toContract(event);
			String payload;
			try {
				payload = json.writeValueAsString(contract);
			}
			catch (com.fasterxml.jackson.core.JsonProcessingException e) {
				return Mono.error(new IllegalStateException("Could not serialize event " + event.eventId(), e));
			}
			DatabaseClient.GenericExecuteSpec spec = client.sql(INSERT)
					.bind("eventId", event.eventId())
					.bind("messageKey", event.patientId().toString())
					.bind("eventType", contract.tipo().value())
					.bind("payload", Json.of(payload))
					.bind("createdAt", event.occurredAt().atOffset(java.time.ZoneOffset.UTC));
			spec = bindOrNull(spec, "correlationId", currentTraceId());
			spec = bindOrNull(spec, "traceparent", currentTraceparent());
			return spec.fetch().rowsUpdated().then();
		});
	}

	private static DatabaseClient.GenericExecuteSpec bindOrNull(DatabaseClient.GenericExecuteSpec spec, String name,
			String value) {
		return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
	}

	/** The trace id of the request that caused the event, used as its correlation id. */
	private String currentTraceId() {
		Tracer current = tracer.getIfAvailable();
		Span span = current == null ? null : current.currentSpan();
		return span == null ? null : span.context().traceId();
	}

	/** The W3C trace context, so the consumer of the event can continue the same trace. */
	private String currentTraceparent() {
		Tracer current = tracer.getIfAvailable();
		Propagator propagation = propagator.getIfAvailable();
		if (current == null || propagation == null || current.currentTraceContext().context() == null) {
			return null;
		}
		java.util.Map<String, String> carrier = new java.util.HashMap<>();
		propagation.inject(current.currentTraceContext().context(), carrier, java.util.Map::put);
		return carrier.get(TriageEvent.TRACEPARENT_HEADER);
	}

}
