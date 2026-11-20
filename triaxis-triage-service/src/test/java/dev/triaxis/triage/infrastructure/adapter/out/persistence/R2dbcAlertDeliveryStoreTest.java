package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.AlertDelivery;
import dev.triaxis.triage.domain.model.AlertOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class R2dbcAlertDeliveryStoreTest {

	@Autowired
	R2dbcAlertDeliveryStore deliveries;

	@Autowired
	DatabaseClient client;

	private static AlertDelivery delivery(AlertOutcome outcome) {
		return new AlertDelivery(UUID.randomUUID(), UUID.randomUUID(), outcome,
				Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	@Test
	void anEventIsHandledOnceItIsRecorded() {
		AlertDelivery delivery = delivery(AlertOutcome.SENT);

		StepVerifier.create(deliveries.isHandled(delivery.eventId())).expectNext(false).verifyComplete();
		StepVerifier.create(deliveries.record(delivery)).expectNext(true).verifyComplete();
		StepVerifier.create(deliveries.isHandled(delivery.eventId())).expectNext(true).verifyComplete();

		client.sql("SELECT patient_id, outcome, handled_at FROM alert_deliveries WHERE event_id = :id")
				.bind("id", delivery.eventId())
				.map(row -> new AlertDelivery(delivery.eventId(), row.get("patient_id", UUID.class),
						AlertOutcome.valueOf(row.get("outcome", String.class)),
						row.get("handled_at", OffsetDateTime.class).toInstant()))
				.one()
				.as(StepVerifier::create)
				.expectNext(delivery)
				.verifyComplete();
	}

	@Test
	void theFirstRecordWins() {
		AlertDelivery failed = delivery(AlertOutcome.FAILED);
		AlertDelivery sentLater = new AlertDelivery(failed.eventId(), failed.patientId(), AlertOutcome.SENT,
				failed.handledAt().plusSeconds(1));

		deliveries.record(failed).block();
		StepVerifier.create(deliveries.record(sentLater)).expectNext(false).verifyComplete();

		String outcome = client.sql("SELECT outcome FROM alert_deliveries WHERE event_id = :id")
				.bind("id", failed.eventId())
				.map(row -> row.get("outcome", String.class))
				.one().block();
		assertThat(outcome).isEqualTo("FAILED");
	}

	@Test
	void theTableOnlyAcceptsTheRecordedOutcomes() {
		StepVerifier.create(client.sql("""
				INSERT INTO alert_deliveries (event_id, patient_id, outcome, handled_at)
				VALUES (:eventId, :patientId, 'DUPLICATE', now())
				""").bind("eventId", UUID.randomUUID()).bind("patientId", UUID.randomUUID()).fetch().rowsUpdated())
				.expectError()
				.verify();
	}

}
