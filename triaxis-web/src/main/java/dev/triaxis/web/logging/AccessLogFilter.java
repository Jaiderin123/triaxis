package dev.triaxis.web.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Writes one structured line per request: method, path, status, duration and user. It never logs bodies,
 * headers, tokens or query strings. Health probes are skipped to keep the log readable. The user is captured
 * by {@link PrincipalCaptureFilter}, which runs after authentication.
 */
public class AccessLogFilter implements WebFilter {

	private static final Logger access = LoggerFactory.getLogger("triaxis.access");

	static final String USER_ATTRIBUTE = AccessLogFilter.class.getName() + ".USER";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (path.startsWith("/actuator/health")) {
			return chain.filter(exchange);
		}
		long start = System.nanoTime();
		return chain.filter(exchange).doFinally(signal -> {
			Object user = exchange.getAttribute(USER_ATTRIBUTE);
			access.atInfo()
					.addKeyValue("http.request.method", exchange.getRequest().getMethod().name())
					.addKeyValue("url.path", path)
					.addKeyValue("http.response.status_code",
							exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value())
					.addKeyValue("event.duration_ms", (System.nanoTime() - start) / 1_000_000)
					.addKeyValue("user.name", user == null ? "anonymous" : user.toString())
					.log("request completed");
		});
	}

}
