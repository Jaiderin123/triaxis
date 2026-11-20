package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.time.ZoneOffset;
import java.util.UUID;

import dev.triaxis.triage.application.port.out.AlertDeliveryStore;
import dev.triaxis.triage.domain.model.AlertDelivery;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Table {@code alert_deliveries}; the event id is the primary key, so the first record of an event wins. */
@Repository
class R2dbcAlertDeliveryStore implements AlertDeliveryStore {

	private static final String EXISTS = "SELECT EXISTS (SELECT 1 FROM alert_deliveries WHERE event_id = :eventId) AS handled";

	private static final String INSERT = """
			INSERT INTO alert_deliveries (event_id, patient_id, outcome, handled_at)
			VALUES (:eventId, :patientId, :outcome, :handledAt)
			ON CONFLICT (event_id) DO NOTHING
			""";

	private final DatabaseClient client;

	R2dbcAlertDeliveryStore(DatabaseClient client) {
		this.client = client;
	}

	@Override
	public Mono<Boolean> isHandled(UUID eventId) {
		return client.sql(EXISTS)
				.bind("eventId", eventId)
				.map(row -> Boolean.TRUE.equals(row.get("handled", Boolean.class)))
				.one();
	}

	@Override
	public Mono<Boolean> record(AlertDelivery delivery) {
		return client.sql(INSERT)
				.bind("eventId", delivery.eventId())
				.bind("patientId", delivery.patientId())
				.bind("outcome", delivery.outcome().name())
				.bind("handledAt", delivery.handledAt().atOffset(ZoneOffset.UTC))
				.fetch()
				.rowsUpdated()
				.map(rows -> rows > 0);
	}

}
