package dev.triaxis.triage.infrastructure.adapter.out.alert;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.triaxis.triage.infrastructure.adapter.out.alert.WebhookAlertNotifier.AlertChannelException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the notifier against a fake webhook receiver on a random local port. */
class WebhookAlertNotifierTest {

	private static final String SECRET_PATH = "/hooks/secret-token-123";

	private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	private final BlockingQueue<String> bodies = new LinkedBlockingQueue<>();
	private final AtomicReference<String> contentType = new AtomicReference<>();
	private final AtomicReference<HttpResponseStatus> answer = new AtomicReference<>(HttpResponseStatus.OK);
	/** When set, the receiver answers only after this signal completes (a slow webhook). */
	private final AtomicReference<Sinks.Empty<Void>> hold = new AtomicReference<>();
	private DisposableServer server;

	@BeforeEach
	void start() {
		server = HttpServer.create().host("127.0.0.1").port(0)
				.route(routes -> routes.post(SECRET_PATH, (request, response) -> request.receive().aggregate().asString()
						.doOnNext(body -> {
							bodies.add(body);
							contentType.set(request.requestHeaders().get("Content-Type"));
						})
						.then(hold.get() == null ? Mono.empty() : hold.get().asMono())
						.then(response.status(answer.get()).send())))
				.bindNow();
	}

	@AfterEach
	void stop() {
		Sinks.Empty<Void> held = hold.get();
		if (held != null) {
			held.tryEmitEmpty();
		}
		server.disposeNow();
	}

	private WebhookAlertNotifier notifier(Duration responseTimeout) {
		URI url = URI.create("http://127.0.0.1:" + server.port() + SECRET_PATH);
		return new WebhookAlertNotifier(url, Duration.ofSeconds(2), responseTimeout, json);
	}

	@Test
	void postsTheTextAndTheStructuredFields() throws Exception {
		StepVerifier.create(notifier(Duration.ofSeconds(5)).send(AlertSamples.alert())).verifyComplete();

		JsonNode body = json.readTree(bodies.poll(5, TimeUnit.SECONDS));
		assertThat(contentType.get()).startsWith("application/json");
		assertThat(body.get("text").asText()).startsWith("Paciente escalado: nivel CRITICO, 12 min de espera");
		assertThat(body.get("tipo").asText()).isEqualTo("paciente_escalado");
		assertThat(body.get("pacienteId").asText()).isEqualTo(AlertSamples.PATIENT_ID.toString());
		assertThat(body.get("nivel").asText()).isEqualTo("CRITICO");
		assertThat(body.get("slaMinutos").asInt()).isEqualTo(5);
		assertThat(body.get("minutosEsperando").asLong()).isEqualTo(12);
		assertThat(body.get("escaladoEn").asText()).isEqualTo("2026-11-20T15:30:00Z");
		assertThat(body.get("ruta").asText()).isEqualTo("/triage/pacientes/" + AlertSamples.PATIENT_ID);
		assertThat(body.properties()).hasSize(8);
	}

	@Test
	void anAnswerOtherThan2xxIsAnErrorWithoutTheUrl() {
		answer.set(HttpResponseStatus.SERVICE_UNAVAILABLE);

		StepVerifier.create(notifier(Duration.ofSeconds(5)).send(AlertSamples.alert()))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(AlertChannelException.class)
							.hasMessage("the webhook answered 503")
							.hasNoCause();
					assertThat(error.toString()).doesNotContain("secret-token-123", "127.0.0.1");
				})
				.verify(Duration.ofSeconds(10));
	}

	@Test
	void aSlowWebhookIsCutByTheTimeout() {
		hold.set(Sinks.empty());

		StepVerifier.create(notifier(Duration.ofMillis(300)).send(AlertSamples.alert()))
				.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AlertChannelException.class)
						.hasMessage("the webhook did not answer in time")
						.hasNoCause())
				.verify(Duration.ofSeconds(5));
	}

	@Test
	void anUnreachableWebhookIsAnErrorWithoutTheUrl() {
		int port = server.port();
		server.disposeNow();
		WebhookAlertNotifier notifier = new WebhookAlertNotifier(URI.create("http://127.0.0.1:" + port + SECRET_PATH),
				Duration.ofSeconds(2), Duration.ofSeconds(2), json);

		StepVerifier.create(notifier.send(AlertSamples.alert()))
				.expectErrorSatisfies(error -> assertThat(error.toString()).doesNotContain("secret-token-123"))
				.verify(Duration.ofSeconds(10));
		server = HttpServer.create().port(0).bindNow();
	}

}
