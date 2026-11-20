package dev.triaxis.triage.infrastructure.adapter.out.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.triaxis.common.event.EventType;
import dev.triaxis.common.event.TriageEvent;
import dev.triaxis.triage.TriageIntegrationTest;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.infrastructure.adapter.out.persistence.PersistenceTestSupport;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.infrastructure.config.OutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@TriageIntegrationTest
class OutboxRelayTest {

	@Autowired
	OutboxRelay relay;

	@Autowired
	org.springframework.test.web.reactive.server.WebTestClient client;

	@Autowired
	KafkaTemplate<String, String> kafka;

	@Autowired
	DatabaseClient database;

	@Autowired
	TransactionalOperator transaction;

	@Autowired
	OutboxProperties properties;

	@Autowired
	MeterRegistry meters;

	@Autowired
	ObjectMapper json;

	@Autowired
	PatientRepository patients;

	@Autowired
	EventPublisher events;

	@Autowired
	UnitOfWork unitOfWork;

	/** Drains whatever other tests left pending so each test starts from an empty outbox. */
	private void drain() {
		int sent;
		do {
			sent = relay.relayOnce().block();
		}
		while (sent > 0);
	}

	/** Stores a patient and its registration event as the use case does, and returns the patient id. */
	private UUID registerPatientWithEvent() {
		Patient patient = PersistenceTestSupport.newPatient();
		unitOfWork.execute(patients.save(patient)
				.then(events.publish(PatientEvent.registered(UUID.randomUUID(), patient)))).block();
		return patient.id();
	}

	/**
	 * Reads the records of the given keys from the beginning of the topic with a plain consumer (test code may
	 * block). It waits for at least {@code minimum} records and then one more second, to notice duplicates.
	 */
	private List<ConsumerRecord<String, String>> readTopic(Set<String> keys, int minimum) {
		Map<String, Object> config = new HashMap<>(kafka.getProducerFactory().getConfigurationProperties());
		Map<String, Object> consumerConfig = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
				ConsumerConfig.GROUP_ID_CONFIG, "relay-test-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		List<ConsumerRecord<String, String>> found = new ArrayList<>();
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig)) {
			consumer.subscribe(List.of(TriageEvent.TOPIC));
			long deadline = System.currentTimeMillis() + 20_000;
			long quietSince = -1;
			while (System.currentTimeMillis() < deadline) {
				int before = found.size();
				consumer.poll(Duration.ofMillis(500)).forEach(record -> {
					if (keys.contains(record.key())) {
						found.add(record);
					}
				});
				if (found.size() >= minimum) {
					if (found.size() != before) {
						quietSince = System.currentTimeMillis();
					}
					else if (quietSince < 0) {
						quietSince = System.currentTimeMillis();
					}
					else if (System.currentTimeMillis() - quietSince >= 1_000) {
						break;
					}
				}
			}
		}
		return found;
	}

	private boolean isPublished(UUID patientId) {
		return Boolean.TRUE.equals(database.sql("SELECT bool_and(published_at IS NOT NULL) AS done FROM outbox_events WHERE message_key = :k")
				.bind("k", patientId.toString()).map(row -> row.get("done", Boolean.class)).one().block());
	}

	private static String header(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		return header == null ? null : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	void deliversPendingEventsToKafkaWithKeyHeadersAndContractPayload() throws Exception {
		drain();
		UUID patientId = registerPatientWithEvent();

		assertThat(relay.relayOnce().block()).isEqualTo(1);

		List<ConsumerRecord<String, String>> records = readTopic(Set.of(patientId.toString()), 1);
		assertThat(records).hasSize(1);
		ConsumerRecord<String, String> record = records.get(0);
		assertThat(record.key()).isEqualTo(patientId.toString());
		assertThat(header(record, "eventType")).isEqualTo("paciente_registrado");
		TriageEvent event = json.readValue(record.value(), TriageEvent.class);
		assertThat(event.tipo()).isEqualTo(EventType.PACIENTE_REGISTRADO);
		assertThat(event.pacienteId()).isEqualTo(patientId);
		assertThat(isPublished(patientId)).isTrue();
	}

	@Test
	void anEventIsDeliveredOnlyOnce() {
		drain();
		UUID patientId = registerPatientWithEvent();

		assertThat(relay.relayOnce().block()).isEqualTo(1);
		assertThat(relay.relayOnce().block()).isZero();

		assertThat(readTopic(Set.of(patientId.toString()), 1)).hasSize(1);
	}

	@Test
	void keepsTheOrderOfTheEventsOfAPatient() {
		drain();
		Patient patient = PersistenceTestSupport.newPatient();
		UUID first = UUID.randomUUID();
		unitOfWork.execute(patients.save(patient).then(events.publish(PatientEvent.registered(first, patient)))).block();
		for (var status : List.of(dev.triaxis.triage.domain.model.PatientStatus.IN_CONSULTATION,
				dev.triaxis.triage.domain.model.PatientStatus.DISCHARGED)) {
			Patient moved = new Patient(patient.id(), patient.fullName(), patient.age(), patient.vitalSigns(),
					patient.chiefComplaint(), patient.level(), patient.slaMinutes(), status, false, null,
					patient.registeredAt(), patient.registeredAt(), null);
			unitOfWork.execute(events.publish(PatientEvent.statusChanged(UUID.randomUUID(), moved,
					dev.triaxis.triage.domain.model.PatientStatus.REGISTERED, patient.registeredAt()))).block();
		}

		assertThat(relay.relayOnce().block()).isEqualTo(3);

		assertThat(readTopic(Set.of(patient.id().toString()), 3)).extracting(r -> header(r, "eventType"))
				.containsExactly("paciente_registrado", "paciente_en_consulta", "paciente_alta");
	}

	@Test
	void aCycleSendsAtMostOneBatch() {
		drain();
		int batch = properties.relay().batchSize();
		for (int i = 0; i < batch + 3; i++) {
			registerPatientWithEvent();
		}

		assertThat(relay.relayOnce().block()).isEqualTo(batch);
		assertThat(relay.relayOnce().block()).isEqualTo(3);
		assertThat(relay.relayOnce().block()).isZero();
	}

	@Test
	void eventsAreNotLostWhenKafkaIsUnavailable() {
		drain();
		UUID patientId = registerPatientWithEvent();
		double failuresBefore = meters.counter("triage.outbox.failures").count();

		// A relay whose producer points to a broker that does not answer.
		DefaultKafkaProducerFactory<String, String> unreachable = new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
				ProducerConfig.MAX_BLOCK_MS_CONFIG, 500,
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
		OutboxRelay brokenRelay = new OutboxRelay(database, transaction, new KafkaTemplate<>(unreachable), properties,
				meters);

		assertThat(brokenRelay.relayOnce().block()).isZero();

		assertThat(isPublished(patientId)).isFalse();
		assertThat(meters.counter("triage.outbox.failures").count()).isGreaterThan(failuresBefore);
		assertThat(database.sql("SELECT count(*) AS total FROM outbox_events WHERE published_at IS NULL")
				.map(row -> row.get("total", Long.class)).one().block()).isGreaterThanOrEqualTo(1L);

		// Kafka comes back: the same event is delivered by the normal relay.
		assertThat(relay.relayOnce().block()).isEqualTo(1);
		assertThat(isPublished(patientId)).isTrue();
		assertThat(readTopic(Set.of(patientId.toString()), 1)).hasSize(1);
		assertThat(meters.get("triage.outbox.pending").gauge().value()).isZero();
	}

	@Test
	void twoRelaysRunningTogetherNeverSendTheSameEventTwice() {
		drain();
		List<UUID> patientIds = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			patientIds.add(registerPatientWithEvent());
		}

		Mono.zip(relay.relayOnce(), relay.relayOnce(), Mono.delay(Duration.ofMillis(1)).then(relay.relayOnce())).block();
		drain();

		Set<String> keys = patientIds.stream().map(UUID::toString).collect(Collectors.toSet());
		List<ConsumerRecord<String, String>> records = readTopic(keys, 20);
		assertThat(records).hasSize(20);
		assertThat(records.stream().map(ConsumerRecord::key).collect(Collectors.toSet())).isEqualTo(keys);
	}

	@Test
	void sendsTheTraceOfTheRequestInTheKafkaHeaders() {
		drain();
		String traceId = "0af7651916cd43dd8448eb211c80319c";
		String id = client.post().uri("/triage/pacientes")
				.header("Authorization", "Bearer " + dev.triaxis.triage.TestSecurityConfiguration.token("nurse-ana", "NURSE"))
				.header("traceparent", "00-" + traceId + "-b7ad6b7169203331-01")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.bodyValue(Map.ofEntries(Map.entry("nombreCompleto", "Ana Gomez"), Map.entry("edad", 54),
						Map.entry("frecuenciaCardiaca", 72), Map.entry("presionSistolica", 120),
						Map.entry("presionDiastolica", 80), Map.entry("spo2", 99), Map.entry("temperatura", 36.5),
						Map.entry("dolor", 2), Map.entry("gcs", 15), Map.entry("quejaPrincipal", "Consulta")))
				.exchange().expectStatus().isCreated().expectBody(Map.class).returnResult().getResponseBody()
				.get("id").toString();

		relay.relayOnce().block();

		List<ConsumerRecord<String, String>> records = readTopic(Set.of(id), 1);
		assertThat(records).hasSize(1);
		assertThat(header(records.get(0), "correlationId")).isEqualTo(traceId);
		assertThat(header(records.get(0), "traceparent")).startsWith("00-" + traceId + "-");
	}

	@Test
	void theEventsTopicHasThreePartitions() throws Exception {
		Map<String, Object> config = new HashMap<>(kafka.getProducerFactory().getConfigurationProperties());
		try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
				Map.of("bootstrap.servers", config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)))) {
			var description = admin.describeTopics(List.of(TriageEvent.TOPIC)).allTopicNames().get();
			assertThat(description.get(TriageEvent.TOPIC).partitions()).hasSize(3);
		}
	}

}
