package dev.triaxis.triage.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.application.port.out.AlertDeliveryStore;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import static dev.triaxis.triage.infrastructure.adapter.in.messaging.AlertTestSupport.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The listener with the default log channel, fed with real Kafka records. Other tests share the topic, so the
 * assertions only look at the alerts of the patients of each test.
 */
@TriageIntegrationTest
class EscalationAlertListenerTest {

	/** Logger of the log channel (LoggingAlertNotifier.LOGGER). */
	private static final String ALERTS_LOGGER = "dev.triaxis.triage.alerts";

	@Autowired
	KafkaTemplate<String, String> kafka;

	@Autowired
	ObjectMapper json;

	@Autowired
	PatientRepository patients;

	@Autowired
	AlertDeliveryStore deliveries;

	@Autowired
	MeterRegistry meters;

	private final Logger logger = (Logger) LoggerFactory.getLogger(ALERTS_LOGGER);
	private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

	@BeforeEach
	void capture() {
		lines.start();
		logger.addAppender(lines);
	}

	@AfterEach
	void release() {
		logger.detachAppender(lines);
	}

	private Patient escalatedPatient() {
		Instant registeredAt = PersistenceTestSupport.nextEpoch();
		return patients.save(PersistenceTestSupport.registeredAt(registeredAt, PatientStatus.REGISTERED)).block();
	}

	private TriageEvent event(UUID eventId, EventType type, Patient patient, Duration after) {
		return new TriageEvent(eventId, type, 1, patient.registeredAt().plus(after), patient.id(), EventLevel.CRITICO,
				EventStatus.REGISTRADO, EventStatus.REGISTRADO, 5, type == EventType.PACIENTE_ESCALADO);
	}

	private void send(TriageEvent event) throws Exception {
		kafka.send(TriageEvent.TOPIC, event.pacienteId().toString(), json.writeValueAsString(event)).get();
	}

	private List<ILoggingEvent> alertsOf(Patient patient) {
		synchronized (lines.list) {
			return lines.list.stream()
					.filter(line -> line.getFormattedMessage().contains(patient.id().toString()))
					.toList();
		}
	}

	private double counted(String outcome) {
		return meters.counter("triage.alerts", "outcome", outcome).count();
	}

	@Test
	void anEscalatedEventGivesExactlyOneAlertAndARedeliveryIsADuplicate() throws Exception {
		Patient patient = escalatedPatient();
		TriageEvent escalated = event(UUID.randomUUID(), EventType.PACIENTE_ESCALADO, patient, Duration.ofMinutes(12));
		double duplicatesBefore = counted("duplicate");

		send(escalated);
		await("the alert", () -> deliveries.isHandled(escalated.idEvento()).block());
		send(escalated);
		await("the duplicate", () -> counted("duplicate") >= duplicatesBefore + 1);

		assertThat(alertsOf(patient)).singleElement().satisfies(line -> {
			assertThat(line.getFormattedMessage())
					.isEqualTo("Paciente escalado: nivel CRITICO, 12 min de espera (SLA 5 min). Ver /triage/pacientes/"
							+ patient.id());
			assertThat(line.getFormattedMessage()).doesNotContain(patient.fullName(), patient.chiefComplaint());
			assertThat(line.getKeyValuePairs()).noneSatisfy(pair -> assertThat(String.valueOf(pair.value))
					.contains(patient.fullName()));
		});
	}

	@Test
	void otherEventTypesAndInvalidRecordsDoNotAlert() throws Exception {
		Patient patient = escalatedPatient();
		double invalidBefore = meters.counter("triage.alerts.invalid").count();
		UUID registeredId = UUID.randomUUID();

		send(event(registeredId, EventType.PACIENTE_REGISTRADO, patient, Duration.ZERO));
		send(event(UUID.randomUUID(), EventType.PACIENTE_EN_CONSULTA, patient, Duration.ofMinutes(1)));
		kafka.send(TriageEvent.TOPIC, patient.id().toString(), "{ this is not an event").get();
		kafka.send(TriageEvent.TOPIC, patient.id().toString(),
				"{\"tipo\": \"paciente_escalado\", \"pacienteId\": \"" + patient.id() + "\"}").get();
		// An escalation sent last: once it is alerted, the records before it in the partition were consumed.
		TriageEvent escalated = event(UUID.randomUUID(), EventType.PACIENTE_ESCALADO, patient, Duration.ofMinutes(6));
		send(escalated);

		await("the escalation after the other records", () -> deliveries.isHandled(escalated.idEvento()).block());
		await("both invalid records counted", () -> meters.counter("triage.alerts.invalid").count() >= invalidBefore + 2);
		assertThat(alertsOf(patient)).hasSize(1);
		assertThat(deliveries.isHandled(registeredId).block()).isFalse();
	}

}
