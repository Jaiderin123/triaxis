package dev.triaxis.security.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * The routes (method and path pattern) declared by a set of functional endpoints, so a test can call every one of
 * them and check that each has an explicit security rule (the shared filter chain denies everything else).
 */
public final class RouteInventory {

	/** One declared route; {@code samplePath} replaces every path variable with a UUID. */
	public record Route(HttpMethod method, String pattern) {

		public String samplePath() {
			return pattern.replaceAll("\\{[^/]+}", "00000000-0000-0000-0000-000000000000");
		}

	}

	private RouteInventory() {
	}

	public static List<Route> of(Collection<? extends RouterFunction<?>> functions) {
		List<Route> routes = new ArrayList<>();
		functions.forEach(function -> function.accept(new Collector(routes)));
		return routes;
	}

	private static final class Collector implements RouterFunctions.Visitor, RequestPredicates.Visitor {

		private final List<Route> routes;
		private HttpMethod method;
		private String path;

		Collector(List<Route> routes) {
			this.routes = routes;
		}

		@Override
		public void route(RequestPredicate predicate, HandlerFunction<?> handler) {
			method = null;
			path = null;
			predicate.accept(this);
			if (method != null && path != null) {
				routes.add(new Route(method, path));
			}
		}

		@Override
		public void method(Set<HttpMethod> methods) {
			method = methods.iterator().next();
		}

		@Override
		public void path(String pattern) {
			path = pattern;
		}

		@Override
		public void startNested(RequestPredicate predicate) {
		}

		@Override
		public void endNested(RequestPredicate predicate) {
		}

		@Override
		public void resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {
		}

		@Override
		public void attributes(Map<String, Object> attributes) {
		}

		@Override
		public void unknown(RouterFunction<?> routerFunction) {
		}

		@Override
		public void pathExtension(String extension) {
		}

		@Override
		public void header(String name, String value) {
		}

		@Override
		public void queryParam(String name, String value) {
		}

		@Override
		public void startAnd() {
		}

		@Override
		public void and() {
		}

		@Override
		public void endAnd() {
		}

		@Override
		public void startOr() {
		}

		@Override
		public void or() {
		}

		@Override
		public void endOr() {
		}

		@Override
		public void startNegate() {
		}

		@Override
		public void endNegate() {
		}

		@Override
		public void unknown(RequestPredicate predicate) {
		}

	}

}
