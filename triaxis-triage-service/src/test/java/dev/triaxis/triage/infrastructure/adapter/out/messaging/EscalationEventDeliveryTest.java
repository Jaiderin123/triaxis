package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.EventLevel;
import dev.triaxis.common.event.EventStatus;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.application.service.EscalateOverduePatientsService;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class EscalationEventDeliveryTest {

	@Autowired
	PatientRepository patients;

	@Autowired
	AuditRepository audit;

	@Autowired
	EventPublisher events;

	@Autowired
	IdGenerator ids;

	@Autowired
	UnitOfWork unitOfWork;

	@Autowired
	TriageMetrics metrics;

	@Autowired
	OutboxRelay relay;

	@Autowired
	KafkaTemplate<String, String> kafka;

	@Autowired
	ObjectMapper json;

	@Test
	void theEscalationEventReachesKafkaWithTheLevelAndTheFlag() throws Exception {
		Instant epoch = PersistenceTestSupport.nextEpoch();
		Patient patient = patients.save(PersistenceTestSupport.registeredAt(epoch, PatientStatus.REGISTERED)).block();
		Instant now = epoch.plus(Duration.ofDays(1));
		new EscalateOverduePatientsService(patients, audit, events, ids, () -> now, unitOfWork, metrics, 100)
				.escalateOverdue().block();

		int sent;
		do {
			sent = relay.relayOnce().block();
		}
		while (sent > 0);

		ConsumerRecord<String, String> record = readOne(patient.id().toString());
		assertThat(new String(record.headers().lastHeader(TriageEvent.EVENT_TYPE_HEADER).value()))
				.isEqualTo("paciente_escalado");
		TriageEvent event = json.readValue(record.value(), TriageEvent.class);
		assertThat(event.tipo()).isEqualTo(EventType.PACIENTE_ESCALADO);
		assertThat(event.pacienteId()).isEqualTo(patient.id());
		assertThat(event.nivel()).isEqualTo(EventLevel.CRITICO);
		assertThat(event.estadoAnterior()).isEqualTo(EventStatus.REGISTRADO);
		assertThat(event.estadoNuevo()).isEqualTo(EventStatus.REGISTRADO);
		assertThat(event.escalado()).isTrue();
		assertThat(event.slaMinutos()).isEqualTo(5);
		assertThat(event.ocurridoEn()).isEqualTo(now);
	}

	/** Reads the topic from the beginning with a plain consumer (test code may block) until the key appears. */
	private ConsumerRecord<String, String> readOne(String key) {
		Object servers = kafka.getProducerFactory().getConfigurationProperties()
				.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
		Map<String, Object> config = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers,
				ConsumerConfig.GROUP_ID_CONFIG, "escalation-test-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		List<ConsumerRecord<String, String>> found = new ArrayList<>();
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
			consumer.subscribe(List.of(TriageEvent.TOPIC));
			long deadline = System.currentTimeMillis() + 20_000;
			while (found.isEmpty() && System.currentTimeMillis() < deadline) {
				consumer.poll(Duration.ofMillis(500)).forEach(record -> {
					if (key.equals(record.key())) {
						found.add(record);
					}
				});
			}
		}
		assertThat(found).as("records of patient %s", key).hasSize(1);
		return found.get(0);
	}

}
