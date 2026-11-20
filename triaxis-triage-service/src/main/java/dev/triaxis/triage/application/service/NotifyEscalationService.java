package dev.triaxis.triage.application.service;

import java.time.Duration;

import dev.triaxis.triage.application.port.in.NotifyEscalationUseCase;
import dev.triaxis.triage.application.port.out.AlertDeliveryStore;
import dev.triaxis.triage.application.port.out.AlertNotifier;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.domain.model.AlertDelivery;
import dev.triaxis.triage.domain.model.AlertOutcome;
import dev.triaxis.triage.domain.model.EscalationAlert;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientEventType;
import dev.triaxis.triage.domain.model.PatientStatus;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetrySpec;

/**
 * Sends the alert of an escalation event at most once (ADR 0011, ASSUMPTIONS A45). An event already recorded is a
 * duplicate and is not sent. A patient that no longer waits (in consultation or discharged) is skipped: the alert
 * would come too late to help, which also covers the replay of old events by a new consumer group (ADR 0023,
 * ASSUMPTIONS A46). Otherwise the channel gets up to {@code attempts} tries, waiting {@code backoff} and
 * then twice as long before each new try; when they all fail the alert is given up as {@code FAILED}, so one
 * channel outage never holds the next events. The outcome is recorded after the send: a crash between both can
 * repeat one alert, which A45 accepts.
 */
public class NotifyEscalationService implements NotifyEscalationUseCase {

	private final PatientRepository patients;
	private final AlertDeliveryStore deliveries;
	private final AlertNotifier notifier;
	private final ClockPort clock;
	private final TriageMetrics metrics;
	private final Retry retry;

	public NotifyEscalationService(PatientRepository patients, AlertDeliveryStore deliveries, AlertNotifier notifier,
			ClockPort clock, TriageMetrics metrics, int attempts, Duration backoff) {
		if (attempts < 1) {
			throw new IllegalArgumentException("The alert needs at least 1 attempt");
		}
		if (backoff == null || backoff.isZero() || backoff.isNegative()) {
			throw new IllegalArgumentException("The alert back-off must be positive");
		}
		this.patients = patients;
		this.deliveries = deliveries;
		this.notifier = notifier;
		this.clock = clock;
		this.metrics = metrics;
		this.retry = attempts == 1 ? RetrySpec.max(0) : Retry.backoff(attempts - 1L, backoff).jitter(0);
	}

	@Override
	public Mono<AlertOutcome> handle(PatientEvent event) {
		return Mono.defer(() -> {
			if (event.type() != PatientEventType.ESCALATED) {
				return Mono.error(new IllegalArgumentException("Event " + event.eventId() + " is not an escalation"));
			}
			return deliveries.isHandled(event.eventId())
					.flatMap(handled -> handled ? Mono.just(AlertOutcome.DUPLICATE) : deliver(event))
					.doOnNext(metrics::alertHandled);
		});
	}

	private Mono<AlertOutcome> deliver(PatientEvent event) {
		return patients.findById(event.patientId())
				.flatMap(patient -> patient.status() == PatientStatus.REGISTERED
						? alertOf(event, patient).flatMap(this::send)
						: Mono.just(AlertOutcome.SKIPPED))
				// No patient, or an event that precedes its registration: there is nothing sensible to send.
				.defaultIfEmpty(AlertOutcome.FAILED)
				.flatMap(outcome -> deliveries.record(new AlertDelivery(event.eventId(), event.patientId(), outcome,
						clock.now())).thenReturn(outcome));
	}

	private static Mono<EscalationAlert> alertOf(PatientEvent event, Patient patient) {
		try {
			return Mono.just(EscalationAlert.of(event, patient.registeredAt()));
		}
		catch (IllegalArgumentException inconsistent) {
			return Mono.empty();
		}
	}

	private Mono<AlertOutcome> send(EscalationAlert alert) {
		return notifier.send(alert)
				.retryWhen(retry)
				.thenReturn(AlertOutcome.SENT)
				.onErrorReturn(AlertOutcome.FAILED);
	}

}
