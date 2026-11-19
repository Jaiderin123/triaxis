package dev.triaxis.triage.infrastructure.adapter.in.web.handler;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import dev.triaxis.triage.application.port.in.ChangePatientStatusUseCase;
import dev.triaxis.triage.application.port.in.GetPatientUseCase;
import dev.triaxis.triage.application.port.in.RegisterPatientUseCase;
import dev.triaxis.triage.application.port.out.ClockPort;
import dev.triaxis.triage.domain.model.PatientStatus;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.ChangeStatusRequest;
import dev.triaxis.triage.infrastructure.adapter.in.web.dto.RegisterPatientRequest;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.ExternalValues;
import dev.triaxis.triage.infrastructure.adapter.in.web.mapper.PatientWebMapper;
import dev.triaxis.triage.infrastructure.adapter.in.web.validation.InvalidRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Translates HTTP to use case calls and back; business rules live in the domain. */
@Component
public class PatientHandler {

	private final RegisterPatientUseCase registerPatient;
	private final ChangePatientStatusUseCase changeStatus;
	private final GetPatientUseCase getPatient;
	private final PatientWebMapper mapper;
	private final ClockPort clock;

	PatientHandler(RegisterPatientUseCase registerPatient, ChangePatientStatusUseCase changeStatus,
			GetPatientUseCase getPatient, PatientWebMapper mapper, ClockPort clock) {
		this.registerPatient = registerPatient;
		this.changeStatus = changeStatus;
		this.getPatient = getPatient;
		this.mapper = mapper;
		this.clock = clock;
	}

	public Mono<ServerResponse> register(ServerRequest request) {
		return body(request, RegisterPatientRequest.class)
				.flatMap(body -> actor(request)
						.flatMap(actor -> registerPatient.register(mapper.toRegistrationData(body), actor)))
				.flatMap(patient -> ServerResponse
						.created(URI.create("/triage/pacientes/" + patient.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(mapper.toRegistrationResponse(patient)));
	}

	public Mono<ServerResponse> changeStatus(ServerRequest request) {
		UUID id = patientId(request);
		return body(request, ChangeStatusRequest.class)
				.map(this::targetStatus)
				.flatMap(target -> actor(request).flatMap(actor -> changeStatus.changeStatus(id, target, actor)))
				.flatMap(this::ok);
	}

	public Mono<ServerResponse> get(ServerRequest request) {
		return getPatient.get(patientId(request)).flatMap(this::ok);
	}

	private Mono<ServerResponse> ok(dev.triaxis.triage.domain.model.Patient patient) {
		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
				.bodyValue(mapper.toResponse(patient, clock.now()));
	}

	private <T> Mono<T> body(ServerRequest request, Class<T> type) {
		return request.bodyToMono(type)
				.switchIfEmpty(Mono.error(() -> new InvalidRequestException("cuerpo", "is required")));
	}

	private Mono<String> actor(ServerRequest request) {
		return request.principal().map(Principal::getName);
	}

	private PatientStatus targetStatus(ChangeStatusRequest body) {
		PatientStatus status = body.estado() == null ? null : ExternalValues.parseStatus(body.estado());
		if (status == null) {
			throw new InvalidRequestException("estado", "must be one of " + ExternalValues.allowedStatuses());
		}
		return status;
	}

	private UUID patientId(ServerRequest request) {
		try {
			return UUID.fromString(request.pathVariable("id"));
		}
		catch (IllegalArgumentException e) {
			throw new InvalidRequestException("id", "must be a valid UUID");
		}
	}

}
