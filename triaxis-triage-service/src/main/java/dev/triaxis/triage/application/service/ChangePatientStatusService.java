package dev.triaxis.triage.application.service;

import java.util.UUID;

import dev.triaxis.triage.application.port.in.ChangePatientStatusUseCase;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.domain.exception.ConcurrentStatusChangeException;
import dev.triaxis.triage.domain.exception.PatientNotFoundException;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.service.StatusTransitionPolicy;
import reactor.core.publisher.Mono;

public class ChangePatientStatusService implements ChangePatientStatusUseCase {

	private final PatientRepository patients;
	private final AuditRepository audit;
	private final EventPublisher events;
	private final IdGenerator ids;
	private final ClockPort clock;
	private final UnitOfWork unitOfWork;
	private final StatusTransitionPolicy policy;

	public ChangePatientStatusService(PatientRepository patients, AuditRepository audit, EventPublisher events,
			IdGenerator ids, ClockPort clock, UnitOfWork unitOfWork, StatusTransitionPolicy policy) {
		this.patients = patients;
		this.audit = audit;
		this.events = events;
		this.ids = ids;
		this.clock = clock;
		this.unitOfWork = unitOfWork;
		this.policy = policy;
	}

	@Override
	public Mono<Patient> changeStatus(UUID patientId, PatientStatus target, String actor) {
		return patients.findById(patientId)
				.switchIfEmpty(Mono.error(() -> new PatientNotFoundException(patientId)))
				.flatMap(current -> Mono.fromRunnable(() -> policy.ensureAllowed(current.status(), target))
						.then(applyChange(current, target, actor)))
				.onErrorResume(ConcurrentStatusChangeException.class, conflict -> patients.findById(patientId)
						.switchIfEmpty(Mono.error(() -> new PatientNotFoundException(patientId)))
						.then(Mono.error(conflict)));
	}

	private Mono<Patient> applyChange(Patient current, PatientStatus target, String actor) {
		return Mono.defer(() -> {
			var now = clock.now();
			return unitOfWork.execute(patients.updateStatus(current.id(), current.status(), target, now)
					.switchIfEmpty(Mono.error(() -> new ConcurrentStatusChangeException(current.id())))
					.flatMap(updated -> audit.append(new AuditEntry(updated.id(), actor, eventFor(target),
							current.status(), target, updated.escalated(), now))
							.then(ids.next())
							.flatMap(eventId -> events.publish(
									PatientEvent.statusChanged(eventId, updated, current.status(), now)))
							.thenReturn(updated)));
		});
	}

	private static AuditEventType eventFor(PatientStatus target) {
		return switch (target) {
			case IN_CONSULTATION -> AuditEventType.CONSULTATION_STARTED;
			case DISCHARGED -> AuditEventType.DISCHARGED;
			case REGISTERED -> throw new IllegalArgumentException("A patient cannot go back to REGISTERED");
		};
	}

}
