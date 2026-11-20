package dev.triaxis.triage.infrastructure.config;

import java.time.Instant;

import dev.triaxis.triage.application.port.in.ChangePatientStatusUseCase;
import dev.triaxis.triage.application.port.in.EscalateOverduePatientsUseCase;
import dev.triaxis.triage.application.port.in.GetPatientUseCase;
import dev.triaxis.triage.application.port.in.RegisterPatientUseCase;
import dev.triaxis.triage.application.port.out.AuditRepository;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.application.port.out.EventPublisher;
import dev.triaxis.triage.application.port.out.IdGenerator;
import dev.triaxis.triage.application.port.out.PatientRepository;
import dev.triaxis.triage.application.port.out.TriageMetrics;
import dev.triaxis.triage.application.port.out.UnitOfWork;
import dev.triaxis.triage.application.service.ChangePatientStatusService;
import dev.triaxis.triage.application.service.EscalateOverduePatientsService;
import dev.triaxis.triage.application.service.GetPatientService;
import dev.triaxis.triage.application.service.RegisterPatientService;
import dev.triaxis.triage.domain.service.ComplaintPolicy;
import dev.triaxis.triage.domain.service.SlaPolicy;
import dev.triaxis.triage.domain.service.StatusTransitionPolicy;
import dev.triaxis.triage.domain.service.TriageClassifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain and application classes. */
@Configuration
@EnableConfigurationProperties({TriageProperties.class, EscalationProperties.class})
class TriageConfiguration {

	@Bean
	ClockPort clockPort() {
		return Instant::now;
	}

	@Bean
	SlaPolicy slaPolicy(TriageProperties properties) {
		return new SlaPolicy(properties.sla().minutes());
	}

	@Bean
	TriageClassifier triageClassifier(TriageProperties properties) {
		return TriageClassifier.withDefaultRules(new ComplaintPolicy(properties.administrativeComplaints()));
	}

	@Bean
	StatusTransitionPolicy statusTransitionPolicy() {
		return new StatusTransitionPolicy();
	}

	@Bean
	RegisterPatientUseCase registerPatientUseCase(PatientRepository patients, AuditRepository audit,
			EventPublisher events, ClockPort clock, IdGenerator ids, UnitOfWork unitOfWork, TriageMetrics metrics, TriageClassifier classifier,
			SlaPolicy sla) {
		return new RegisterPatientService(patients, audit, events, clock, ids, unitOfWork, metrics, classifier, sla);
	}

	@Bean
	ChangePatientStatusUseCase changePatientStatusUseCase(PatientRepository patients, AuditRepository audit,
			EventPublisher events, IdGenerator ids, ClockPort clock, UnitOfWork unitOfWork,
			StatusTransitionPolicy policy) {
		return new ChangePatientStatusService(patients, audit, events, ids, clock, unitOfWork, policy);
	}

	@Bean
	EscalateOverduePatientsUseCase escalateOverduePatientsUseCase(PatientRepository patients, AuditRepository audit,
			EventPublisher events, IdGenerator ids, ClockPort clock, UnitOfWork unitOfWork, TriageMetrics metrics,
			EscalationProperties properties) {
		return new EscalateOverduePatientsService(patients, audit, events, ids, clock, unitOfWork, metrics,
				properties.batchSize());
	}

	@Bean
	GetPatientUseCase getPatientUseCase(PatientRepository patients) {
		return new GetPatientService(patients);
	}

}
