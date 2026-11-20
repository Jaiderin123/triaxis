package dev.triaxis.security;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/** The JWKS download with a real local HTTP server: timeouts, outages and the refresh limit for unknown key ids. */
class JwksDecoderTest {

	private static final String ISSUER = "triaxis-auth";
	private static final String AUDIENCE = "triaxis";

	private static KeyPair keys;

	private HttpServer server;
	private final AtomicInteger downloads = new AtomicInteger();
	private final AtomicLong delayMillis = new AtomicLong();
	private volatile boolean failing;
	private volatile String kid = "key-1";

	@BeforeAll
	static void generateKeys() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keys = generator.generateKeyPair();
	}

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/jwks", exchange -> {
			downloads.incrementAndGet();
			if (failing) {
				exchange.sendResponseHeaders(503, -1);
				exchange.close();
				return;
			}
			try {
				Thread.sleep(delayMillis.get());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] body = new JWKSet(new RSAKey.Builder((RSAPublicKey) keys.getPublic()).keyID(kid).build())
					.toString().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String uri() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
	}

	private static ReactiveJwtDecoder decoder(String uri, Duration responseTimeout, Duration cooldown) {
		return TriaxisJwtDecoders.fromJwkSetUri(uri, ISSUER, AUDIENCE,
				new JwksSettings(Duration.ofMillis(500), responseTimeout, cooldown));
	}

	private static String token(String keyId) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("nurse").issuer(ISSUER).audience(AUDIENCE)
				.claim("roles", List.of("NURSE")).issueTime(new Date())
				.expirationTime(Date.from(Instant.now().plusSeconds(600))).build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims);
		jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
		return jwt.serialize();
	}

	@Test
	void aTokenIsValidatedWithTheDownloadedKeysAndTheKeysAreCached() throws Exception {
		ReactiveJwtDecoder decoder = decoder(uri(), Duration.ofSeconds(2), Duration.ofSeconds(30));

		for (int i = 0; i < 3; i++) {
			StepVerifier.create(decoder.decode(token("key-1"))).expectNextCount(1).verifyComplete();
		}
		assertThat(downloads).hasValue(1);
	}

	@Test
	void aSlowJwksEndpointFailsWithinTheResponseTimeout() throws Exception {
		delayMillis.set(5_000);
		ReactiveJwtDecoder decoder = decoder(uri(), Duration.ofMillis(300), Duration.ofSeconds(30));

		long start = System.nanoTime();
		StepVerifier.create(decoder.decode(token("key-1"))).expectError().verify(Duration.ofSeconds(4));
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
	}

	@Test
	void anUnreachableJwksEndpointFailsQuicklyAndIsRetriedOnTheNextRequest() throws Exception {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		ReactiveJwtDecoder down = decoder("http://127.0.0.1:" + closedPort + "/jwks", Duration.ofMillis(500),
				Duration.ofSeconds(30));

		StepVerifier.create(down.decode(token("key-1"))).expectError().verify(Duration.ofSeconds(3));

		ReactiveJwtDecoder recovering = decoder(uri(), Duration.ofSeconds(2), Duration.ofSeconds(30));
		delayMillis.set(5_000);
		StepVerifier.create(recovering.decode(token("key-1"))).expectError().verify(Duration.ofSeconds(6));
		delayMillis.set(0);
		StepVerifier.create(recovering.decode(token("key-1"))).expectNextCount(1).verifyComplete();
	}

	@Test
	void tokensWithUnknownKeyIdsDoNotTriggerADownloadEach() throws Exception {
		ReactiveJwtDecoder decoder = decoder(uri(), Duration.ofSeconds(2), Duration.ofMinutes(5));
		StepVerifier.create(decoder.decode(token("key-1"))).expectNextCount(1).verifyComplete();

		for (int i = 0; i < 20; i++) {
			StepVerifier.create(decoder.decode(token(UUID.randomUUID().toString()))).expectError().verify();
		}

		assertThat(downloads.get()).as("first download plus at most one refresh").isLessThanOrEqualTo(2);
	}

	@Test
	void aRotatedKeyIsFoundAfterTheCooldown() throws Exception {
		ReactiveJwtDecoder decoder = decoder(uri(), Duration.ofSeconds(2), Duration.ZERO);
		StepVerifier.create(decoder.decode(token("key-1"))).expectNextCount(1).verifyComplete();

		kid = "key-2";

		StepVerifier.create(decoder.decode(token("key-2"))).expectNextCount(1).verifyComplete();
		assertThat(downloads).hasValue(2);
	}

	/** An unknown key id while auth is down must not invalidate the keys already known. */
	@Test
	void aFailedRefreshKeepsTheKnownKeys() throws Exception {
		ReactiveJwtDecoder decoder = decoder(uri(), Duration.ofSeconds(2), Duration.ZERO);
		StepVerifier.create(decoder.decode(token("key-1"))).expectNextCount(1).verifyComplete();

		failing = true;
		StepVerifier.create(decoder.decode(token("unknown-key"))).expectError().verify();
		int downloadsAfterTheFailedRefresh = downloads.get();

		for (int i = 0; i < 5; i++) {
			StepVerifier.create(decoder.decode(token("key-1"))).expectNextCount(1).verifyComplete();
		}
		assertThat(downloads.get()).as("valid tokens use the known keys").isEqualTo(downloadsAfterTheFailedRefresh);
	}

	@Test
	void concurrentRequestsShareOneDownload() throws Exception {
		delayMillis.set(300);
		ReactiveJwtDecoder decoder = decoder(uri(), Duration.ofSeconds(2), Duration.ofSeconds(30));
		String token = token("key-1");

		reactor.core.publisher.Flux.range(0, 8).flatMap(i -> decoder.decode(token)).collectList().block();

		assertThat(downloads).hasValue(1);
	}

	@Test
	void theSettingsRejectInvalidValues() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new JwksSettings(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
