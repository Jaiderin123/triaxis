package dev.triaxis.auth.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
		@NotBlank @Size(max = 100) String username,
		@NotBlank @Size(max = 200) String password) {
}
