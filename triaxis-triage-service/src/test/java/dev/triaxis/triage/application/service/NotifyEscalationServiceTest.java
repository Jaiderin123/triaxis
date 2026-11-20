package dev.triaxis.triage.application.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.triage.application.port.out.AlertDeliveryStore;
import dev.triaxis.triage.application.port.out.AlertNotifier;
import dev.triaxis.triage.domain.model.AlertDelivery;
import dev.triaxis.triage.domain.model.AlertOutcome;
import dev.triaxis.triage.domain.model.EscalationAlert;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import dev.triaxis.triage.domain.model.VitalSigns;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyEscalationServiceTest {

	private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:18:00Z");
	private static final Instant ESCALATED_AT = REGISTERED_AT.plusSeconds(12 * 60);

	private final FakeTriageStore store = new FakeTriageStore();
	private final FakeDeliveries deliveries = new FakeDeliveries();
	private final FakeChannel channel = new FakeChannel();

	private NotifyEscalationService service(int attempts) {
		return new NotifyEscalationService(store, deliveries, channel, store, store, attempts, Duration.ofMillis(1));
	}

	private PatientEvent escalatedPatient(int number) {
		UUID id = UUID.fromString("00000000-0000-0000-0000-%012d".formatted(number));
		Patient patient = new Patient(id, "Ana Gomez", 54,
				new VitalSigns(130, 180, 110, 97, new BigDecimal("36.8"), 8, 15), "Dolor toracico",
				TriageLevel.CRITICAL, 5, PatientStatus.REGISTERED, false, null, REGISTERED_AT, REGISTERED_AT, null)
				.escalate(ESCALATED_AT);
		store.patients.put(id, patient);
		return PatientEvent.escalated(UUID.fromString("00000000-0000-0000-0000-%012d".formatted(1000 + number)),
				patient);
	}

	@Test
	void anEscalationGivesExactlyOneAlertWithTheMinimalContent() {
		PatientEvent event = escalatedPatient(1);

		StepVerifier.create(service(3).handle(event)).expectNext(AlertOutcome.SENT).verifyComplete();

		assertThat(channel.sent).singleElement().satisfies(alert -> {
			assertThat(alert.eventId()).isEqualTo(event.eventId());
			assertThat(alert.patientId()).isEqualTo(event.patientId());
			assertThat(alert.level()).isEqualTo(TriageLevel.CRITICAL);
			assertThat(alert.slaMinutes()).isEqualTo(5);
			assertThat(alert.minutesWaiting()).isEqualTo(12);
			assertThat(alert.escalatedAt()).isEqualTo(ESCALATED_AT);
		});
		assertThat(deliveries.recorded.get(event.eventId()))
				.isEqualTo(new AlertDelivery(event.eventId(), event.patientId(), AlertOutcome.SENT, store.now));
		assertThat(store.alertOutcomes).containsExactly(AlertOutcome.SENT);
	}

	@Test
	void theSameEventTwiceGivesOneAlertAndOneDuplicate() {
		PatientEvent event = escalatedPatient(1);
		NotifyEscalationService service = service(3);

		service.handle(event).block();
		StepVerifier.create(service.handle(event)).expectNext(AlertOutcome.DUPLICATE).verifyComplete();

		assertThat(channel.sent).hasSize(1);
		assertThat(deliveries.recorded.get(event.eventId()).outcome()).isEqualTo(AlertOutcome.SENT);
		assertThat(store.alertOutcomes).containsExactly(AlertOutcome.SENT, AlertOutcome.DUPLICATE);
	}

	@Test
	void aChannelThatFailsTwiceAndThenAnswersSendsOneAlert() {
		PatientEvent event = escalatedPatient(1);
		channel.failuresLeft.set(2);

		StepVerifier.create(service(3).handle(event)).expectNext(AlertOutcome.SENT).verifyComplete();

		assertThat(channel.calls).hasValue(3);
		assertThat(channel.sent).hasSize(1);
		assertThat(store.alertOutcomes).containsExactly(AlertOutcome.SENT);
	}

	@Test
	void aChannelThatKeepsFailingIsCountedAsFailedAndTheNextEventIsStillSent() {
		PatientEvent first = escalatedPatient(1);
		PatientEvent second = escalatedPatient(2);
		NotifyEscalationService service = service(3);
		channel.failuresLeft.set(3);

		StepVerifier.create(service.handle(first)).expectNext(AlertOutcome.FAILED).verifyComplete();
		StepVerifier.create(service.handle(second)).expectNext(AlertOutcome.SENT).verifyComplete();

		assertThat(channel.calls).hasValue(4);
		assertThat(channel.sent).extracting(EscalationAlert::eventId).containsExactly(second.eventId());
		assertThat(deliveries.recorded.get(first.eventId()).outcome()).isEqualTo(AlertOutcome.FAILED);
		assertThat(store.alertOutcomes).containsExactly(AlertOutcome.FAILED, AlertOutcome.SENT);
	}

	@Test
	void aFailedEventIsNotSentAgain() {
		PatientEvent event = escalatedPatient(1);
		NotifyEscalationService service = service(1);
		channel.failuresLeft.set(1);

		service.handle(event).block();
		StepVerifier.create(service.handle(event)).expectNext(AlertOutcome.DUPLICATE).verifyComplete();

		assertThat(channel.calls).hasValue(1);
	}

	@Test
	void anUnknownPatientIsCountedAsFailedWithoutCallingTheChannel() {
		PatientEvent event = escalatedPatient(1);
		store.patients.clear();

		StepVerifier.create(service(3).handle(event)).expectNext(AlertOutcome.FAILED).verifyComplete();

		assertThat(channel.calls).hasValue(0);
		assertThat(deliveries.recorded.get(event.eventId()).outcome()).isEqualTo(AlertOutcome.FAILED);
	}

	@Test
	void aPatientThatNoLongerWaitsIsSkippedWithoutCallingTheChannel() {
		PatientEvent inConsultation = escalatedPatient(1);
		PatientEvent discharged = escalatedPatient(2);
		store.updateStatus(inConsultation.patientId(), PatientStatus.REGISTERED, PatientStatus.IN_CONSULTATION,
				ESCALATED_AT.plusSeconds(60)).block();
		store.updateStatus(discharged.patientId(), PatientStatus.REGISTERED, PatientStatus.IN_CONSULTATION,
				ESCALATED_AT.plusSeconds(60)).block();
		store.updateStatus(discharged.patientId(), PatientStatus.IN_CONSULTATION, PatientStatus.DISCHARGED,
				ESCALATED_AT.plusSeconds(120)).block();
		NotifyEscalationService service = service(3);

		StepVerifier.create(service.handle(inConsultation)).expectNext(AlertOutcome.SKIPPED).verifyComplete();
		StepVerifier.create(service.handle(discharged)).expectNext(AlertOutcome.SKIPPED).verifyComplete();
		// Recorded: a redelivery is a duplicate, even if the patient were waiting again.
		StepVerifier.create(service.handle(inConsultation)).expectNext(AlertOutcome.DUPLICATE).verifyComplete();

		assertThat(channel.calls).hasValue(0);
		assertThat(deliveries.recorded.get(inConsultation.eventId()).outcome()).isEqualTo(AlertOutcome.SKIPPED);
		assertThat(store.alertOutcomes).containsExactly(AlertOutcome.SKIPPED, AlertOutcome.SKIPPED,
				AlertOutcome.DUPLICATE);
	}

	@Test
	void aFailureOfTheDeliveryStoreIsAnErrorSoTheEventIsHandledAgain() {
		PatientEvent event = escalatedPatient(1);
		deliveries.failReads = true;

		StepVerifier.create(service(3).handle(event)).expectErrorMessage("deliveries unavailable").verify();

		assertThat(channel.calls).hasValue(0);
		assertThat(store.alertOutcomes).isEmpty();
	}

	@Test
	void otherEventTypesAreRefused() {
		PatientEvent event = escalatedPatient(1);
		PatientEvent registered = new PatientEvent(event.eventId(), PatientEventType.REGISTERED,
				REGISTERED_AT, event.patientId(), event.level(), null, PatientStatus.REGISTERED, 5, false);

		StepVerifier.create(service(3).handle(registered)).expectError(IllegalArgumentException.class).verify();

		assertThat(channel.calls).hasValue(0);
	}

	@Test
	void theSettingsAreValidated() {
		assertThatThrownBy(() -> service(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NotifyEscalationService(store, deliveries, channel, store, store, 3, Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** Records the handled events; {@code record} keeps the first outcome, like the primary key of the table. */
	static final class FakeDeliveries implements AlertDeliveryStore {

		final Map<UUID, AlertDelivery> recorded = new LinkedHashMap<>();
		boolean failReads;

		@Override
		public Mono<Boolean> isHandled(UUID eventId) {
			return Mono.defer(() -> failReads ? Mono.error(new IllegalStateException("deliveries unavailable"))
					: Mono.just(recorded.containsKey(eventId)));
		}

		@Override
		public Mono<Boolean> record(AlertDelivery delivery) {
			return Mono.fromSupplier(() -> recorded.putIfAbsent(delivery.eventId(), delivery) == null);
		}

	}

	/** Fails the given number of calls, then accepts every alert. */
	static final class FakeChannel implements AlertNotifier {

		final List<EscalationAlert> sent = new ArrayList<>();
		final AtomicInteger calls = new AtomicInteger();
		final AtomicInteger failuresLeft = new AtomicInteger();

		@Override
		public Mono<Void> send(EscalationAlert alert) {
			return Mono.defer(() -> {
				calls.incrementAndGet();
				if (failuresLeft.getAndDecrement() > 0) {
					return Mono.error(new IllegalStateException("channel unavailable"));
				}
				sent.add(alert);
				return Mono.<Void>empty();
			});
		}

	}

}
