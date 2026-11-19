package dev.triaxis.triage.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.triage.TestcontainersConfiguration;
import dev.triaxis.triage.application.service.ChangePatientStatusService;
import dev.triaxis.triage.domain.exception.ConcurrentStatusChangeException;
import dev.triaxis.triage.domain.exception.InvalidStatusTransitionException;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.service.StatusTransitionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrentStatusChangeTest {

	@Autowired
	R2dbcPatientRepository patients;

	@Autowired
	R2dbcAuditRepository audit;

	@Autowired
	TransactionalUnitOfWork unitOfWork;

	@Autowired
	DatabaseClient client;

	@Test
	void onlyOneOfTwoSimultaneousRequestsWins() {
		ChangePatientStatusService service = new ChangePatientStatusService(patients, audit, Instant::now, unitOfWork,
				new StatusTransitionPolicy());
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();

		for (int round = 0; round < 25; round++) {
			Patient patient = PersistenceTestSupport.newPatient();
			patients.save(patient).block();

			List<Object> outcomes = Flux.merge(attempt(service, patient.id(), "nurse"),
					attempt(service, patient.id(), "doctor")).collectList().block();

			long winners = outcomes.stream().filter(o -> o instanceof Patient).count();
			assertThat(winners).as("round %d", round).isEqualTo(1);
			outcomes.stream().filter(o -> !(o instanceof Patient)).forEach(error -> {
				assertThat(error).isInstanceOfAny(ConcurrentStatusChangeException.class,
						InvalidStatusTransitionException.class);
				conflicts.incrementAndGet();
			});
			successes.addAndGet((int) winners);

			long consultationEntries = client.sql("""
					SELECT count(*) AS total FROM patient_audit
					WHERE patient_id = :id AND event_type = 'CONSULTATION_STARTED'
					""").bind("id", patient.id()).map(row -> row.get("total", Long.class)).one().block();
			assertThat(consultationEntries).as("audit entries in round %d", round).isEqualTo(1);
			assertThat(patients.findById(patient.id()).block().status()).isEqualTo(PatientStatus.IN_CONSULTATION);
		}

		assertThat(successes.get()).isEqualTo(25);
		assertThat(conflicts.get()).isEqualTo(25);
	}

	private static Mono<Object> attempt(ChangePatientStatusService service, UUID id, String actor) {
		return service.changeStatus(id, PatientStatus.IN_CONSULTATION, actor)
				.map(patient -> (Object) patient)
				.onErrorResume(error -> Mono.just(error));
	}

}
