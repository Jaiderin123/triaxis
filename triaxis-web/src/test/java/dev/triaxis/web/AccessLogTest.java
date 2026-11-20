package dev.triaxis.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = WebTestApp.class, properties = {
		"logging.structured.format.console=ecs",
		"management.endpoint.health.probes.enabled=true"
})
@AutoConfigureWebTestClient
class AccessLogTest {

	@Autowired
	WebTestClient client;

	@Test
	void logsOneLinePerRequestWithTheUser(CapturedOutput output) {
		client.get().uri("/secured").headers(h -> h.setBasicAuth("ana", "secret")).exchange().expectStatus().isOk();

		// The line is written when the exchange finishes, which can be just after the response is received.
		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(output.getAll())
				.contains("request completed").contains("\"url\":{\"path\":\"/secured\"}")
				.contains("\"user\":{\"name\":\"ana\"}").contains("\"status_code\":200"));
	}

	@Test
	void logsRejectedRequestsAsAnonymous(CapturedOutput output) {
		client.get().uri("/secured").exchange().expectStatus().isUnauthorized();

		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(output.getAll())
				.contains("\"status_code\":401").contains("\"user\":{\"name\":\"anonymous\"}"));
	}

	@Test
	void neverLogsBodiesOrCredentials(CapturedOutput output) {
		client.post().uri("/echo").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"nombre\":\"top-secret-name\"}")
				.exchange().expectStatus().isOk();
		client.get().uri("/secured").headers(h -> h.setBasicAuth("ana", "secret")).exchange();

		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(output.getAll()).contains("\"url\":{\"path\":\"/echo\"}"));
		assertThat(output.getAll()).doesNotContain("top-secret-name").doesNotContain("Basic YW5h");
	}

	@Test
	void skipsTheHealthProbes(CapturedOutput output) {
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();

		assertThat(output.getAll()).doesNotContain("/actuator/health/liveness");
	}

}
