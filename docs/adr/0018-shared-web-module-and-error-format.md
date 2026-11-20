# 0018. Shared web module and a single error format

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

Three services expose HTTP APIs. By phase 3 the code that writes an error response was copied in the auth and triage services, the 401 and 403 answers came from Spring Security with a different shape, and the error bodies carried no trace id. The assistant service would have been a third copy. Functional endpoints have no controller advice (ADR 0012), so error handling is written by hand in every service.

## Decision

- Create the library module **`triaxis-web`** with the WebFlux plumbing that every service needs and that holds no business logic:
  - `ProblemWriter`: the only place that writes an error response. It sets the HTTP status and the body field `status` from the same value, so they cannot differ, and adds `instance` (the path), `traceId` and `timestamp`.
  - `AbstractProblemExceptionHandler`: the base of each service's global exception handler. A service only maps its own exceptions (`mapDomain`); the base handles malformed bodies (naming the field), unsupported media types, oversized bodies (413), unknown routes and unexpected errors (a generic 500 that never leaks details).
  - `ProblemAuthenticationEntryPoint` and `ProblemAccessDeniedHandler`: 401 and 403 in the same format, with `WWW-Authenticate`.
  - The access log filter (one structured line per request, no bodies or tokens) and the protected metrics endpoint (ADR 0019 and 0020).
- Every error of every service uses RFC 7807 with the fields `type`, `title`, `status`, `detail`, `instance`, `traceId` and `timestamp`. Validation errors add `errores`, a list of `campo` and `mensaje`.
- `triaxis-security` depends on `triaxis-web`. The auth service depends on `triaxis-web` but not on `triaxis-security`, so ADR 0003 still holds.

## Alternatives considered

- **Copy the code in each service:** no new module, but three copies drift apart and the 401 and 403 answers stay different.
- **Put it in `triaxis-common`:** that module is the event contract and has no Spring dependencies; mixing would couple the contract to the web stack.
- **Put it in `triaxis-security`:** the auth service does not validate tokens and should not depend on that module.

## Consequences

- A change of the error format is made in one place.
- A new service gets the standard errors, the access log and the metrics protection by adding one dependency.
- The module is a deliberate extra dependency of every service; it must stay small and free of business rules.
