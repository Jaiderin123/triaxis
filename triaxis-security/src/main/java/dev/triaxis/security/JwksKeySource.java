package dev.triaxis.security;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Signing keys from the JWKS endpoint, downloaded on first use with explicit connect and response timeouts. The
 * key set is cached. A token whose key id is not in the cache triggers a new download (the keys may have been
 * rotated) at most once per {@code refreshCooldown}; in between, such a token simply finds no key and is rejected.
 * A failed download is not cached, so the next request tries again within the same limits.
 */
final class JwksKeySource implements Function<SignedJWT, Flux<JWK>> {

	private final WebClient web;
	private final String uri;
	private final JwksSettings settings;
	private final Clock clock;
	private Mono<JWKSet> keys;
	private Instant lastDownload = Instant.MIN;

	JwksKeySource(String uri, JwksSettings settings, Clock clock) {
		HttpClient http = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) settings.connectTimeout().toMillis())
				.responseTimeout(settings.responseTimeout());
		this.web = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
		this.uri = uri;
		this.settings = settings;
		this.clock = clock;
	}

	@Override
	public Flux<JWK> apply(SignedJWT token) {
		JWKSelector selector = new JWKSelector(JWKMatcher.forJWSHeader(token.getHeader()));
		return current().flatMapMany(set -> {
			List<JWK> found = selector.select(set);
			return found.isEmpty() ? refreshIfAllowed().flatMapIterable(selector::select) : Flux.fromIterable(found);
		});
	}

	private synchronized Mono<JWKSet> current() {
		if (keys == null) {
			keys = download();
		}
		return keys;
	}

	/** A new download for an unknown key id, unless one happened less than {@code refreshCooldown} ago. */
	private synchronized Mono<JWKSet> refreshIfAllowed() {
		Instant now = clock.instant();
		if (Duration.between(lastDownload, now).compareTo(settings.refreshCooldown()) < 0) {
			return Mono.empty();
		}
		keys = download();
		return keys;
	}

	private synchronized Mono<JWKSet> download() {
		lastDownload = clock.instant();
		Mono<JWKSet> fetch = web.get().uri(uri).retrieve().bodyToMono(String.class)
				// Second limit on the whole exchange, in case the response trickles in.
				.timeout(settings.connectTimeout().plus(settings.responseTimeout()))
				.map(JwksKeySource::parse);
		Mono<JWKSet> cached = fetch.cache(set -> Duration.ofMillis(Long.MAX_VALUE), error -> Duration.ZERO,
				() -> Duration.ZERO);
		return cached.doOnError(error -> forget(cached));
	}

	private synchronized void forget(Mono<JWKSet> failed) {
		if (keys == failed) {
			keys = null;
		}
	}

	private static JWKSet parse(String body) {
		try {
			return JWKSet.parse(body);
		}
		catch (ParseException e) {
			throw new IllegalStateException("The JWKS answer is not a valid key set", e);
		}
	}

}
