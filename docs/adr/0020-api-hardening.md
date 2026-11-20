# 0020. API hardening: audience, body limit, rate limit and protected metrics

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The requirements specification asks for authentication, input validation and no hardcoded credentials. Beyond that, a clinical API should not accept tokens meant for another system, unbounded bodies, unlimited password guessing or open operational endpoints.

## Decision

- **Audience:** the auth service adds `aud` to every token (`auth.jwt.audience`, default `triaxis`) and the resource servers reject tokens without it (`triaxis.security.audience`). This complements ADR 0003.
- **Body size:** `spring.codec.max-in-memory-size` is 64 KB. A larger body answers 413 in the standard format.
- **Rate limit on `POST /auth/token`:** at most 10 attempts per minute per username and per client address (fixed window, in memory; configurable). The 11th answers 429 with `Retry-After`. With several replicas each one limits on its own, and behind a proxy the address is the proxy's, so the per-username limit is the main protection.
- **Metrics endpoint:** `/actuator/prometheus` requires HTTP Basic with credentials from `METRICS_USER` and `METRICS_PASSWORD` (a Kubernetes Secret in deployment). Without a password the endpoint is denied to everybody. The Ingress does not route `/actuator`. Health probes stay public. No other actuator endpoint is exposed.
- **Errors and logs:** answers never include stack traces or internal messages for unexpected errors, 401 and 403 are generic, and logs never contain bodies, tokens or passwords.
- **Also in place:** strict JSON (no silent conversion of types), BCrypt for passwords, a dummy hash for unknown users, RS256 only, secrets only from the environment.

## Alternatives considered

- **No audience check:** simpler, but any token signed by the same issuer for another purpose would be accepted.
- **A distributed rate limiter (Redis):** works across replicas but adds a dependency to the auth service, which has no datastore.
- **A separate management port for metrics:** also valid; it adds configuration and security setup without much extra protection once Basic authentication and the Ingress rules are in place.
- **Protecting metrics with a JWT:** a scraper cannot easily renew 15-minute tokens.

## Consequences

- Tokens are bound to this system; changing the audience needs a coordinated change of the auth service and the resource servers.
- Brute force is slowed down but not stopped; account lockout and an identity provider with its own protections are the production answer.
- TLS termination, non-root containers and network policies are deployment tasks (ADR 0010).
