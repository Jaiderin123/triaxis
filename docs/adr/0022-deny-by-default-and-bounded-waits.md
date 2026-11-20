# 0022. Deny by default, bounded waits and UTC sessions

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

A full review of phases 0 to 5 found no broken requirement, but it did find several places where the code relied on library defaults that are unsafe in production:

- **Security rules.** The resource servers ended their rules with `anyExchange().authenticated()`, so a route added without its role rule was open to every role.
- **Expiration.** The default timestamp validator accepted a token without `exp`.
- **JWKS download.** The download had no timeout, and a token with an unknown key id triggered a new download on every request.
- **Database waits.** `r2dbc-pool` waits forever by default to acquire or create a connection, and no statement had a time limit. One stuck call could stop the escalation loop or the outbox relay without any error.
- **Session time zone.** The R2DBC session used the time zone of the JVM, so `date_trunc` buckets would move outside UTC.
- **Urgency view marker.** The view was considered built when `urgency:meta` existed, but every event writes that key.

These are cross-cutting choices with alternatives, so they are recorded together.

## Decision

- **Deny by default.** The shared security chain ends with `anyExchange().denyAll()`, after the public health probes, the rules of the service and `/actuator/info` (authenticated). Every service has a test that calls each declared route with a token of every role and fails on a 403 (a `RouteInventory` test fixture of `triaxis-security`). An unknown path answers 403 with a valid token instead of 404.
- **Required claims.** A token without `exp` is refused, in addition to the signature (RS256 only), `iss` and `aud`.
- **Bounded JWKS download.** The download has connect and response timeouts (`triaxis.security.jwks.*`, 2 s and 3 s). The key set is cached. A token with an unknown key id triggers a new download at most once per `refresh-cooldown` (30 s). A failed download is not cached.
- **Bounded database waits.** Both services:
  - set the pool limits: `max-size` 10, `max-acquire-time` 5 s, `max-create-connection-time` 5 s, `max-validation-time` 2 s;
  - set session limits through a `ConnectionFactoryOptionsBuilderCustomizer`: `statement_timeout` 10 s and `lock_timeout` 5 s (`triaxis.database.*`).
- **Bounded background cycles.** Each escalation batch has a timeout (`triage.escalation.batch-timeout`), and so does each relay cycle (`triage.outbox.relay.cycle-timeout`, rolled back and retried). One stuck cycle never stops the loop.
- **Bounded similarity search.** The search of the assistant has a timeout (`assistant.rag.search-timeout`), and a database failure is a 503.
- **UTC sessions.** The same customizer sets the session time zone to UTC. A customizer is used instead of `spring.r2dbc.properties` because Spring Boot ignores those properties when the connection comes from connection details, which is the case in the Testcontainers tests. Every test JVM runs in `America/Bogota`, so code that depends on the JVM zone fails in the tests.
- **A dedicated marker for the urgency view.** `urgency:initialized` is written only by the reset before a replay. When Redis does not answer at partition assignment, the check is retried and the listener restarted later. The assistant runs as a single replica (ASSUMPTIONS A39).

## Alternatives considered

- **Keep `authenticated()` and rely on review.** That is what let the gap exist: nothing fails when a rule is forgotten.
- **Keep the default JWKS source of Spring Security and only add a `WebClient` with timeouts.** That solves the hang but not the download for every unknown key id.
- **Only reactive `.timeout()` operators, without pool and session limits.** Cancelling a Reactor chain does not end the query on the server, and the connection stays busy. The server-side limits are what free it.
- **`AT TIME ZONE 'UTC'` in every query instead of a UTC session.** It has to be repeated in every query, and one missed query gives wrong buckets.

## Consequences

- A new route fails its service tests until it gets a rule, which protects the report of phase 6 and the later endpoints.
- A slow auth service, database or Redis now produces errors with a stack trace and metrics, instead of requests and loops that hang.
- **Values to tune.** The limits are properties. A real statement slower than 10 s would now fail; none of the current queries is close to that.
- **Duplicated events on a timeout.** A relay cycle cut by its timeout sends its events again (at-least-once delivery, ADR 0005).
- **Accepted debt.** The improvements left from the same review are listed in `PLAN.md`: a dead letter for the outbox, selective retries of the listener, a bulkhead for the LLM, deterministic race tests and a dependency scanner.
