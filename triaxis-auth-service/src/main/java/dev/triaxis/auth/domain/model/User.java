package dev.triaxis.auth.domain.model;

import java.util.Set;

public record User(String username, String passwordHash, Set<Role> roles) {

	public User {
		roles = Set.copyOf(roles);
	}

}
