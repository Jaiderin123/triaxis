package dev.triaxis.triage.infrastructure.adapter.in.scheduler;

import java.time.Duration;
import java.util.UUID;

import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.infrastructure.adapter.out.messaging.OutboxRelay;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import dev.triaxis.triage.infrastructure.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class OutboxRelaySchedulerTest {

	@Autowired
	OutboxRelay relay;

	@Autowired
	DatabaseClient database;

	@Autowired
	PatientRepository patients;

	@Autowired
	EventPublisher events;

	@Autowired
	UnitOfWork unitOfWork;

	@Test
	void deliversPendingEventsPeriodicallyUntilItIsStopped() throws Exception {
		OutboxProperties fast = new OutboxProperties(
				new OutboxProperties.Relay(true, Duration.ofMillis(100), 50, Duration.ofSeconds(15), Duration.ofSeconds(30),
						Duration.ofSeconds(60)),
				new OutboxProperties.Topic(3, (short) 1));
		OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay, fast);
		Patient patient = PersistenceTestSupport.newPatient();
		unitOfWork.execute(patients.save(patient)
				.then(events.publish(PatientEvent.registered(UUID.randomUUID(), patient)))).block();

		scheduler.start();
		try {
			assertThat(scheduler.isRunning()).isTrue();
			boolean published = false;
			for (int attempt = 0; attempt < 100 && !published; attempt++) {
				Thread.sleep(100);
				published = Boolean.TRUE.equals(database
						.sql("SELECT bool_and(published_at IS NOT NULL) AS done FROM outbox_events WHERE message_key = :k")
						.bind("k", patient.id().toString()).map(row -> row.get("done", Boolean.class)).one().block());
			}
			assertThat(published).as("the event was delivered by the periodic loop").isTrue();
		}
		finally {
			scheduler.stop();
		}
		assertThat(scheduler.isRunning()).isFalse();
	}

}
