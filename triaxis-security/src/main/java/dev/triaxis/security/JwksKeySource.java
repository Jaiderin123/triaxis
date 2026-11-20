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
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Signing keys from the JWKS endpoint, downloaded on first use with explicit connect and response timeouts.
 *
 * <ul>
 * <li>The last key set that was downloaded successfully is kept and always used; a download only replaces it
 * when it succeeds, so an auth service that is down never invalidates keys that are already known.</li>
 * <li>A token whose key id is not known triggers a new download (the keys may have been rotated) at most once
 * per {@code refreshCooldown}; in between, such a token finds no key and is rejected.</li>
 * <li>Concurrent requests share one download in flight. Until a first download succeeds, every request may
 * start one, each bounded by the timeouts.</li>
 * </ul>
 */
final class JwksKeySource implements Function<SignedJWT, Flux<JWK>> {

	private final WebClient web;
	private final String uri;
	private final JwksSettings settings;
	private final Clock clock;
	private volatile JWKSet lastGood;
	private Mono<JWKSet> inFlight;
	private Instant lastRefresh = Instant.MIN;

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
		JWKSet known = lastGood;
		if (known == null) {
			return download().flatMapIterable(selector::select);
		}
		List<JWK> found = selector.select(known);
		return found.isEmpty() ? refreshIfAllowed().flatMapIterable(selector::select) : Flux.fromIterable(found);
	}

	/** A new download for an unknown key id, unless one was started less than {@code refreshCooldown} ago. */
	private synchronized Mono<JWKSet> refreshIfAllowed() {
		Instant now = clock.instant();
		if (Duration.between(lastRefresh, now).compareTo(settings.refreshCooldown()) < 0) {
			return Mono.empty();
		}
		lastRefresh = now;
		// A failed refresh falls back to the keys already known.
		return download().onErrorResume(error -> lastGood == null ? Mono.error(error) : Mono.just(lastGood));
	}

	/** One download at a time; the result replaces the known keys only when it succeeds. */
	private synchronized Mono<JWKSet> download() {
		if (inFlight == null) {
			Mono<JWKSet> fetch = web.get().uri(uri).retrieve().bodyToMono(String.class)
					// Second limit on the whole exchange, in case the response trickles in.
					.timeout(settings.connectTimeout().plus(settings.responseTimeout()))
					.map(JwksKeySource::parse)
					.doOnNext(set -> lastGood = set)
					.doFinally(signal -> finished())
					.cache();
			inFlight = fetch;
		}
		return inFlight;
	}

	private synchronized void finished() {
		inFlight = null;
	}

	private static JWKSet parse(String body) {
		try {
			return JWKSet.parse(body);
		}
		catch (ParseException e) {
			throw new JwtException("The JWKS answer is not a valid key set", e);
		}
	}

}
