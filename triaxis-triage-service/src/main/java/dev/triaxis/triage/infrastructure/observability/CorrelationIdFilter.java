package dev.triaxis.triage.infrastructure.observability;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Gives every request a correlation id: the one sent by the caller when it looks safe, a new one otherwise.
 * It is echoed in the response and travels in the Reactor context, so the outbox can copy it to the events.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter implements WebFilter {

	private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String incoming = exchange.getRequest().getHeaders().getFirst(CorrelationContext.HEADER);
		String correlationId = incoming != null && SAFE.matcher(incoming).matches() ? incoming : newId();
		exchange.getResponse().getHeaders().set(CorrelationContext.HEADER, correlationId);
		return chain.filter(exchange).contextWrite(Context.of(CorrelationContext.KEY, correlationId));
	}

	/** Uniqueness is all that matters here, so a non-blocking random source is enough (no entropy read). */
	static String newId() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return new UUID(random.nextLong(), random.nextLong()).toString();
	}

}
