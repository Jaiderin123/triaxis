package dev.triaxis.triage.application.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import dev.triaxis.triage.domain.exception.InvalidPatientDataException;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.service.ComplaintPolicy;
import dev.triaxis.triage.domain.service.SlaPolicy;
import dev.triaxis.triage.domain.service.TriageClassifier;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterPatientServiceTest {

	private final FakeTriageStore store = new FakeTriageStore();
	private final RegisterPatientService service = new RegisterPatientService(store, store, store, store, store, store,
			TriageClassifier.withDefaultRules(new ComplaintPolicy(Set.of("renovacion de recetario"))),
			new SlaPolicy(Map.of(TriageLevel.CRITICAL, 5, TriageLevel.URGENT, 10, TriageLevel.URGENT_2, 30,
					TriageLevel.NOT_URGENT, 60, TriageLevel.NOT_URGENT_2, 120)));

	private static RegistrationData criticalPatient() {
		return new RegistrationData("Ana Gomez", 54, 130, 180, 110, 97, new BigDecimal("36.8"), 8, 15,
				"Dolor toracico");
	}

	@Test
	void classifiesStoresAndAuditsTheRegistration() {
		StepVerifier.create(service.register(criticalPatient(), "nurse"))
				.assertNext(patient -> {
					assertThat(patient.id()).isEqualTo(store.nextId);
					assertThat(patient.level()).isEqualTo(TriageLevel.CRITICAL);
					assertThat(patient.slaMinutes()).isEqualTo(5);
					assertThat(patient.status()).isEqualTo(PatientStatus.REGISTERED);
					assertThat(patient.registeredAt()).isEqualTo(store.now);
				})
				.verifyComplete();

		assertThat(store.patients).containsKey(store.nextId);
		assertThat(store.audit).singleElement().satisfies((AuditEntry entry) -> {
			assertThat(entry.patientId()).isEqualTo(store.nextId);
			assertThat(entry.actor()).isEqualTo("nurse");
			assertThat(entry.eventType()).isEqualTo(AuditEventType.REGISTERED);
			assertThat(entry.previousStatus()).isNull();
			assertThat(entry.newStatus()).isEqualTo(PatientStatus.REGISTERED);
			assertThat(entry.escalated()).isFalse();
			assertThat(entry.occurredAt()).isEqualTo(store.now);
		});
	}

	@Test
	void countsTheClassificationOnce() {
		service.register(criticalPatient(), "nurse").block();

		assertThat(store.classified).containsExactly(TriageLevel.CRITICAL);
	}

	@Test
	void invalidDataStoresNothingAndCountsNothing() {
		RegistrationData invalid = new RegistrationData("Ana Gomez", 54, 300, 180, 110, 97, new BigDecimal("36.8"), 8,
				15, "Dolor toracico");

		StepVerifier.create(service.register(invalid, "nurse"))
				.expectError(InvalidPatientDataException.class)
				.verify();

		assertThat(store.patients).isEmpty();
		assertThat(store.audit).isEmpty();
		assertThat(store.classified).isEmpty();
	}

	@Test
	void patientIsNotStoredWhenTheAuditEntryCannotBeWritten() {
		store.failAudit = true;

		StepVerifier.create(service.register(criticalPatient(), "nurse"))
				.expectErrorMessage("audit store unavailable")
				.verify();

		assertThat(store.patients).isEmpty();
		assertThat(store.audit).isEmpty();
		assertThat(store.classified).isEmpty();
	}

}
