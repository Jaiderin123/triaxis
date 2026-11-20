package dev.triaxis.triage.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientEvent;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.TriageLevel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of every output port. The unit of work restores the previous state when the
 * work fails, which lets tests prove that a change and its audit entry are committed together.
 */
class FakeTriageStore implements PatientRepository, AuditRepository, EventPublisher, ClockPort, IdGenerator,
		UnitOfWork, TriageMetrics {

	final Map<UUID, Patient> patients = new LinkedHashMap<>();
	final List<AuditEntry> audit = new ArrayList<>();
	final List<PatientEvent> events = new ArrayList<>();
	final List<TriageLevel> classified = new ArrayList<>();
	final List<TriageLevel> escalatedLevels = new ArrayList<>();
	final AtomicInteger committedUnits = new AtomicInteger();

	Instant now = Instant.parse("2026-11-18T15:30:00Z");
	UUID nextId = UUID.fromString("00000000-0000-0000-0000-000000000001");
	boolean failAudit;
	boolean failEvents;
	/** When set, runs after the patient is read and before the transaction starts (simulates a race). */
	Runnable beforeUnitOfWork;

	// PatientRepository

	@Override
	public Mono<Patient> save(Patient patient) {
		return Mono.fromSupplier(() -> {
			patients.put(patient.id(), patient);
			return patient;
		});
	}

	@Override
	public Mono<Patient> findById(UUID id) {
		return Mono.justOrEmpty(patients.get(id));
	}

	@Override
	public Mono<Patient> updateStatus(UUID id, PatientStatus expected, PatientStatus next, Instant at) {
		return Mono.defer(() -> {
			Patient current = patients.get(id);
			if (current == null || current.status() != expected) {
				return Mono.empty();
			}
			Patient updated = new Patient(current.id(), current.fullName(), current.age(), current.vitalSigns(),
					current.chiefComplaint(), current.level(), current.slaMinutes(), next, current.escalated(),
					current.escalatedAt(), current.registeredAt(), at,
					next == PatientStatus.IN_CONSULTATION ? at : current.consultationStartedAt());
			patients.put(id, updated);
			return Mono.just(updated);
		});
	}

	/** Same selection as the SQL statement, expressed with the domain rules. */
	@Override
	public Flux<Patient> escalateOverdue(Instant at, int limit) {
		return Flux.defer(() -> {
			List<Patient> due = patients.values().stream()
					.filter(patient -> patient.escalationDueAt(at))
					.sorted(Comparator.comparing(Patient::registeredAt))
					.limit(limit)
					.map(patient -> patient.escalate(at))
					.toList();
			due.forEach(patient -> patients.put(patient.id(), patient));
			return Flux.fromIterable(due);
		});
	}

	// AuditRepository

	@Override
	public Mono<AuditEntry> append(AuditEntry entry) {
		return Mono.defer(() -> {
			if (failAudit) {
				return Mono.error(new IllegalStateException("audit store unavailable"));
			}
			audit.add(entry);
			return Mono.just(entry);
		});
	}

	// EventPublisher

	@Override
	public Mono<Void> publish(PatientEvent event) {
		return Mono.defer(() -> {
			if (failEvents) {
				return Mono.error(new IllegalStateException("outbox unavailable"));
			}
			events.add(event);
			return Mono.<Void>empty();
		});
	}

	// ClockPort, IdGenerator, TriageMetrics

	@Override
	public Instant now() {
		return now;
	}

	@Override
	public Mono<UUID> next() {
		return Mono.just(nextId);
	}

	@Override
	public void patientClassified(TriageLevel level) {
		classified.add(level);
	}

	@Override
	public void patientEscalated(TriageLevel level) {
		escalatedLevels.add(level);
	}

	// UnitOfWork

	@Override
	public <T> Mono<T> execute(Mono<T> work) {
		return Mono.defer(() -> {
			if (beforeUnitOfWork != null) {
				beforeUnitOfWork.run();
			}
			Map<UUID, Patient> patientsBefore = new LinkedHashMap<>(patients);
			List<AuditEntry> auditBefore = new ArrayList<>(audit);
			List<PatientEvent> eventsBefore = new ArrayList<>(events);
			return work.doOnSuccess(result -> committedUnits.incrementAndGet())
					.doOnError(error -> {
						patients.clear();
						patients.putAll(patientsBefore);
						audit.clear();
						audit.addAll(auditBefore);
						events.clear();
						events.addAll(eventsBefore);
					});
		});
	}

}
