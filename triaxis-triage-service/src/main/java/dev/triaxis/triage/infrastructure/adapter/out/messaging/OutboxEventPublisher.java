package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.infrastructure.observability.CorrelationContext;
import io.r2dbc.postgresql.codec.Json;
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
			INSERT INTO outbox_events (event_id, message_key, event_type, correlation_id, payload, created_at)
			VALUES (:eventId, :messageKey, :eventType, :correlationId, :payload, :createdAt)
			""";

	private final DatabaseClient client;
	private final EventContractMapper mapper;
	private final ObjectMapper json;

	OutboxEventPublisher(DatabaseClient client, EventContractMapper mapper, ObjectMapper json) {
		this.client = client;
		this.mapper = mapper;
		this.json = json;
	}

	@Override
	public Mono<Void> publish(PatientEvent event) {
		return Mono.deferContextual(context -> {
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
			String correlationId = context.getOrDefault(CorrelationContext.KEY, null);
			spec = correlationId == null ? spec.bindNull("correlationId", String.class)
					: spec.bind("correlationId", correlationId);
			return spec.fetch().rowsUpdated().then();
		});
	}

}
