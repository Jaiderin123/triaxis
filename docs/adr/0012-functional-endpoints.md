# 0012. Functional endpoints instead of annotated controllers

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

Spring WebFlux supports two programming models: annotated controllers (`@RestController`) and functional endpoints (`RouterFunction` and `HandlerFunction`). The project wants the web layer to be a thin adapter in the hexagonal layout, with explicit routes that are easy to test and to read.

## Decision

- Use **functional endpoints**. A router bean declares the routes, a handler has one method per route, and each handler only translates HTTP to a use case call and back.
- Because annotation support is not available, the equivalents are explicit:
  - business validation is done by the domain, and the handler checks only the shape of the request (ADR 0017);
  - errors are mapped by a `WebExceptionHandler` to RFC 7807 `ProblemDetail` (controller advice does not apply);
  - authorization rules are declared per path and method in the security filter chain, through the shared `AuthorizationRulesCustomizer`;
  - tests use `WebTestClient` against the real context (and `bindToRouterFunction` for pure web tests).

## Alternatives considered

- **Annotated controllers:** familiar, with `@Valid`, `@ControllerAdvice` and automatic OpenAPI, but routes are scattered in annotations and validation tends to leak into the web layer.
- **A mix of both:** inconsistent for a small code base.

## Consequences

- Routes of a service are visible in one place.
- There is no automatic OpenAPI documentation; the API is described in the README, and a hand-written description or router annotations can be added later.
- Error handling and security rules are written by hand, which is more code but fully visible and tested.
