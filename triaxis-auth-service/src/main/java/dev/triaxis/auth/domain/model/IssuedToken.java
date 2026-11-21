package dev.triaxis.auth.domain.model;

public record IssuedToken(String accessToken, long expiresInSeconds) {

	@Override
	public String toString() {
		return "IssuedToken[accessToken=***, expiresInSeconds=" + expiresInSeconds + "]";
	}

}
