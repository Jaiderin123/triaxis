package dev.triaxis.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WebTestApp.class, properties = {
		"spring.codec.max-in-memory-size=1KB",
		"management.tracing.sampling.probability=1.0",
		"spring.reactor.context-propagation=auto"
})
@AutoConfigureWebTestClient
class ProblemResponsesTest {

	@Autowired
	WebTestClient client;

	private WebTestClient.BodyContentSpec expectProblem(WebTestClient.ResponseSpec response, int status) {
		return response.expectStatus().isEqualTo(status)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(status)
				.jsonPath("$.title").isNotEmpty()
				.jsonPath("$.detail").isNotEmpty()
				.jsonPath("$.instance").isNotEmpty()
				.jsonPath("$.timestamp").isNotEmpty()
				.jsonPath("$.traceId").isNotEmpty();
	}

	@Test
	void everyErrorHasTheSameShapeAndTheBodyStatusMatchesTheHttpStatus() {
		expectProblem(client.get().uri("/domain").exchange(), 409);
		expectProblem(client.get().uri("/teapot").exchange(), 418);
		expectProblem(client.get().uri("/does-not-exist").exchange(), 401); // unknown paths need authentication first
		expectProblem(client.get().uri("/secured").exchange(), 401);
	}

	@Test
	void theInstanceIsTheRequestPath() {
		expectProblem(client.get().uri("/domain").exchange(), 409).jsonPath("$.instance").isEqualTo("/domain");
	}

	@Test
	void unexpectedErrorsDoNotLeakDetails() {
		expectProblem(client.get().uri("/boom").exchange(), 500)
				.jsonPath("$.detail").isEqualTo("Unexpected error");
	}

	@Test
	void unknownRouteForAnAuthenticatedUserIsANotFoundProblem() {
		expectProblem(client.get().uri("/does-not-exist").headers(h -> h.setBasicAuth("ana", "secret")).exchange(), 404);
	}

	@Test
	void unauthenticatedRequestsGetAChallenge() {
		client.get().uri("/secured").exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void forbiddenRequestsUseTheStandardFormat() {
		expectProblem(client.get().uri("/admin").headers(h -> h.setBasicAuth("ana", "secret")).exchange(), 403)
				.jsonPath("$.detail").isEqualTo("Access denied");
	}

	@Test
	void aWrongTypeNamesTheFieldInTheErrorList() {
		expectProblem(client.post().uri("/echo").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"edad\":\"treinta\"}").exchange(), 400)
				.jsonPath("$.errores[0].campo").isEqualTo("edad");
	}

	@Test
	void malformedJsonIsABadRequest() {
		expectProblem(client.post().uri("/echo").contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{not json").exchange(), 400);
	}

	@Test
	void nonJsonBodiesAreUnsupported() {
		expectProblem(client.post().uri("/echo").contentType(MediaType.TEXT_PLAIN).bodyValue("hello").exchange(), 415);
	}

	@Test
	void oversizedBodiesAreRejected() {
		String big = "{\"nombre\":\"" + "x".repeat(4000) + "\"}";

		expectProblem(client.post().uri("/echo").contentType(MediaType.APPLICATION_JSON).bodyValue(big).exchange(), 413);
	}

	@Test
	void validBodiesPass() {
		client.post().uri("/echo").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("edad", 3))
				.exchange().expectStatus().isOk();
	}

	@Test
	void theTraceIdIsAValidTraceId() {
		String traceId = client.get().uri("/domain").exchange().expectBody(Map.class).returnResult()
				.getResponseBody().get("traceId").toString();

		assertThat(traceId).matches("[0-9a-f]{32}");
	}

}
