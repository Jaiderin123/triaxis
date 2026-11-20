package dev.triaxis.auth.application.port.out;

import dev.triaxis.auth.domain.model.User;
import reactor.core.publisher.Mono;

public interface UserRepository {

	Mono<User> findByUsername(String username);

}
