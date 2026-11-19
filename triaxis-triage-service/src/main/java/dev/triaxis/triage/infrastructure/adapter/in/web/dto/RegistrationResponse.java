package dev.triaxis.triage.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(UUID id, String nivel, int slaMinutos, String estado, Instant fechaRegistro) {
}
