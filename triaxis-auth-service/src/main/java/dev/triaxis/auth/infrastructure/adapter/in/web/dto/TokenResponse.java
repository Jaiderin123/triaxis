package dev.triaxis.auth.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("token_type") String tokenType,
		@JsonProperty("expires_in") long expiresIn) {

	/** The token is a bearer credential: a debug log of the encoder must never print it. */
	@Override
	public String toString() {
		return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
	}

}
