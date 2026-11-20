package dev.triaxis.triage;

import java.util.List;

import dev.triaxis.security.test.RouteInventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * The shared filter chain denies every route without an explicit rule. This test calls every route of the
 * service with a token that has every role: a 403 means a route was added without its rule in
 * SecurityRulesConfiguration. The rule per role is tested in the API tests of each endpoint.
 */
@TriageIntegrationTest
class RouteSecurityTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	WebTestClient client;

	@Test
	@SuppressWarnings("rawtypes")
	void everyRouteHasAnExplicitSecurityRule() {
		List<RouteInventory.Route> routes = RouteInventory.of(
				context.getBeansOfType(RouterFunction.class).values().stream().map(f -> (RouterFunction<?>) f).toList());
		assertThat(routes).isNotEmpty();

		for (RouteInventory.Route route : routes) {
			WebTestClient.RequestBodySpec request = client
					.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_NURSE"),
							new SimpleGrantedAuthority("ROLE_DOCTOR")))
					.method(route.method()).uri(route.samplePath());
			WebTestClient.ResponseSpec response = route.method() == HttpMethod.GET ? request.exchange()
					: request.contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();

			assertThat(response.returnResult(String.class).getStatus().value())
					.as("%s %s has no explicit security rule", route.method(), route.pattern())
					.isNotEqualTo(403);
		}
	}

}
