package dev.triaxis.triage.application.service;

import dev.triaxis.triage.application.port.in.RegisterPatientUseCase;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.domain.model.AuditEntry;
import dev.triaxis.triage.domain.model.AuditEventType;
import dev.triaxis.triage.domain.model.Patient;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.domain.model.RegistrationData;
import dev.triaxis.triage.domain.service.SlaPolicy;
import dev.triaxis.triage.domain.service.TriageClassifier;
import reactor.core.publisher.Mono;

public class RegisterPatientService implements RegisterPatientUseCase {

	private final PatientRepository patients;
	private final AuditRepository audit;
	private final ClockPort clock;
	private final IdGenerator ids;
	private final UnitOfWork unitOfWork;
	private final TriageMetrics metrics;
	private final TriageClassifier classifier;
	private final SlaPolicy sla;

	public RegisterPatientService(PatientRepository patients, AuditRepository audit, ClockPort clock,
			IdGenerator ids, UnitOfWork unitOfWork, TriageMetrics metrics, TriageClassifier classifier,
			SlaPolicy sla) {
		this.patients = patients;
		this.audit = audit;
		this.clock = clock;
		this.ids = ids;
		this.unitOfWork = unitOfWork;
		this.metrics = metrics;
		this.classifier = classifier;
		this.sla = sla;
	}

	@Override
	public Mono<Patient> register(RegistrationData data, String actor) {
		return Mono.defer(() -> {
			Patient patient = Patient.register(ids.next(), data, classifier, sla, clock.now());
			return unitOfWork.execute(patients.save(patient)
					.flatMap(saved -> audit.append(new AuditEntry(saved.id(), actor, AuditEventType.REGISTERED,
							null, PatientStatus.REGISTERED, saved.escalated(), saved.registeredAt()))
							.thenReturn(saved)));
		}).doOnSuccess(saved -> {
			if (saved != null) {
				metrics.patientClassified(saved.level());
			}
		});
	}

}
