package dev.triaxis.triage.application.service;

import java.util.List;

import dev.triaxis.triage.application.port.in.EscalateOverduePatientsUseCase;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Flags the overdue patients and, in the same unit of work, writes one audit entry (actor {@code system}) and
 * one {@code paciente_escalado} event per patient. Either all of it is committed or none of it.
 */
public class EscalateOverduePatientsService implements EscalateOverduePatientsUseCase {

	private final PatientRepository patients;
	private final AuditRepository audit;
	private final EventPublisher events;
	private final IdGenerator ids;
	private final ClockPort clock;
	private final UnitOfWork unitOfWork;
	private final TriageMetrics metrics;
	private final int batchSize;

	public EscalateOverduePatientsService(PatientRepository patients, AuditRepository audit, EventPublisher events,
			IdGenerator ids, ClockPort clock, UnitOfWork unitOfWork, TriageMetrics metrics, int batchSize) {
		if (batchSize < 1) {
			throw new IllegalArgumentException("The escalation batch size must be at least 1");
		}
		this.patients = patients;
		this.audit = audit;
		this.events = events;
		this.ids = ids;
		this.clock = clock;
		this.unitOfWork = unitOfWork;
		this.metrics = metrics;
		this.batchSize = batchSize;
	}

	@Override
	public Mono<Integer> escalateOverdue() {
		return Mono.defer(() -> unitOfWork.execute(patients.escalateOverdue(clock.now(), batchSize)
				// The rows are read completely before the next statements use the same connection.
				.collectList()
				.flatMap(escalated -> Flux.fromIterable(escalated)
						.concatMap(this::recordEscalation)
						.then(Mono.just(escalated)))))
				.doOnNext(escalated -> escalated.forEach(patient -> metrics.patientEscalated(patient.level())))
				.map(List::size);
	}

	private Mono<Void> recordEscalation(Patient patient) {
		return audit.append(new AuditEntry(patient.id(), AuditEntry.SYSTEM_ACTOR, AuditEventType.ESCALATED,
				patient.status(), patient.status(), true, patient.escalatedAt()))
				.then(ids.next())
				.flatMap(eventId -> events.publish(PatientEvent.escalated(eventId, patient)));
	}

	@Override
	public int batchSize() {
		return batchSize;
	}

}
