package dev.triaxis.auth.domain.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid credentials");
	}

}
