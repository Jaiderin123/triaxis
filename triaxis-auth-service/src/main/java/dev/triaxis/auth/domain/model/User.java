package dev.triaxis.auth.domain.model;

import java.util.Set;

public record User(String username, String passwordHash, Set<Role> roles) {

	public User {
		roles = Set.copyOf(roles);
	}

	/** Never prints the password hash. */
	@Override
	public String toString() {
		return "User[username=" + username + ", passwordHash=***, roles=" + roles + "]";
	}

}
