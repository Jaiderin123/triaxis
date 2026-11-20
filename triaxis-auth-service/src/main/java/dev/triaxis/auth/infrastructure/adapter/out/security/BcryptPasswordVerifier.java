package dev.triaxis.auth.infrastructure.adapter.out.security;

import java.util.UUID;

import dev.triaxis.auth.application.port.out.PasswordVerifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BcryptPasswordVerifier implements PasswordVerifier {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
	/** Compared against when the user does not exist, so timing does not reveal valid usernames. */
	private final String dummyHash = encoder.encode(UUID.randomUUID().toString());

	@Override
	public boolean verify(String rawPassword, String passwordHash) {
		if (passwordHash == null) {
			encoder.matches(rawPassword, dummyHash);
			return false;
		}
		return encoder.matches(rawPassword, passwordHash);
	}

}
