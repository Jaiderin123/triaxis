package dev.triaxis.web.logging;

import java.security.Principal;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Runs after Spring Security and remembers who made the request, for the access log. */
public class PrincipalCaptureFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return exchange.getPrincipal()
				.map(Principal::getName)
				.doOnNext(name -> exchange.getAttributes().put(AccessLogFilter.USER_ATTRIBUTE, name))
				.then(Mono.defer(() -> chain.filter(exchange)));
	}

}
