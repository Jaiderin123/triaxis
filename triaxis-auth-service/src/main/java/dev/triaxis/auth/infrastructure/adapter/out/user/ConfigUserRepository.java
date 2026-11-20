package dev.triaxis.auth.infrastructure.adapter.out.user;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.triaxis.auth.application.port.out.UserRepository;
import dev.triaxis.auth.domain.model.User;
import dev.triaxis.auth.infrastructure.config.AuthProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Users defined in configuration (there is no user database). */
@Component
class ConfigUserRepository implements UserRepository {

	private final Map<String, User> users;

	ConfigUserRepository(AuthProperties properties) {
		this.users = properties.users().stream()
				.map(entry -> new User(entry.username(), entry.passwordHash(), entry.roles()))
				.collect(Collectors.toUnmodifiableMap(User::username, Function.identity()));
	}

	@Override
	public Mono<User> findByUsername(String username) {
		return Mono.justOrEmpty(users.get(username));
	}

}
