package dev.triaxis.auth.domain.model;

public record IssuedToken(String accessToken, long expiresInSeconds) {
}
