package dev.triaxis.auth.application.port.out;

public interface PasswordVerifier {

	/**
	 * Checks a raw password. When {@code passwordHash} is null (unknown user) the implementation
	 * must still spend the same time as a real check and return false.
	 */
	boolean verify(String rawPassword, String passwordHash);

}
