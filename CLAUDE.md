# CLAUDE.md

Triaxis is a reactive digital triage system with a clinical assistant. Nurses register patients with vital signs, the system classifies them automatically, tracks their clinical state, escalates patients who exceed their waiting-time SLA and keeps an audit trail. Doctors query internal clinical protocols in natural language (RAG) and the assistant must never invent clinical content. Backend only, no frontend.

## Where to find things

| What | Where |
|---|---|
| Stack, versions, naming | `docs/TECH_STACK.md` |
| Architecture, contracts, data model, testing, deployment | `docs/ARCHITECTURE.md` |
| Phased checklist (what to do next) | `docs/PLAN.md` |
| Assumptions and ambiguities | `docs/ASSUMPTIONS.md` |
| Git and commit conventions | `docs/GIT_WORKFLOW.md` |
| Decision records | `docs/adr/` (README, `0000-template.md` and ADR 0013 exist) |
| Original requirements (RF-xx, RN-xx), Spanish | `docs/private/ASSIGNMENT.md` (local only, git-ignored) |

## Modules

| Module | Port | Responsibility |
|---|---|---|
| `triaxis-triage-service` | 8081 | Patients, classification, state flow, SLA escalation, audit, report, alerts (`/triage/**`) |
| `triaxis-assistant-service` | 8082 | RAG over protocols, urgency view from Kafka in Redis (`/asistente/**`) |
| `triaxis-auth-service` | 8083 | Issues RS256 JWTs, exposes JWKS (`/auth/**`, `/.well-known/jwks.json`) |
| `triaxis-common` | n/a | Library: event contract records (`triaje.eventos`) |
| `triaxis-security` | n/a | Library: shared resource-server (JWT) configuration |

All five modules exist as skeletons (Spring Boot 3.5.16) and `./gradlew build` passes; no business logic yet.

## Package layout (hexagonal, per service)

Base package `dev.triaxis.<service>` (for example `dev.triaxis.triage`):

```
domain          model, rule, service, exception      -> depends on the JDK only
application     port.in, port.out, service           -> depends on domain only
infrastructure  adapter.in (web, scheduler, messaging), adapter.out (persistence, messaging, llm, redis, alert), config
```

- `infrastructure` may depend on both; nothing depends on `infrastructure`. ArchUnit tests enforce this.
- The Spanish external contract is mapped in DTOs and mappers at the boundary. The domain uses English names.
- Time comes from a `Clock` port (UTC). Never call `Instant.now()` in domain or application code.
- Web adapters use functional endpoints (`RouterFunction` + handler), not `@RestController`. Errors go through a `WebExceptionHandler` that returns `ProblemDetail`.

## Non-negotiable rules

Stack
- Spring Boot 3.5.x (never Boot 4), Java 21, Spring AI 1.1.x (never 2.x), Gradle Groovy DSL.
- Spring Kafka (not Reactor Kafka). R2DBC only: no JDBC, no Flyway; schema from init scripts.
- Vectors use a custom R2DBC repository over pgvector (no `PgVectorStore`).
- Do not pin versions managed by the Spring Boot BOM. Pin only what `TECH_STACK.md` section 8 lists.

Reactive
- No blocking call on an event-loop thread. Blocking SDK calls (`EmbeddingModel` and similar) run on `Schedulers.boundedElastic()`.
- Bounded concurrency (`flatMap(..., concurrency)`) and explicit timeouts on every remote call.

Correctness
- State changes are conditional `UPDATE ... WHERE status = :expected`. Zero rows means re-read, then 404 or 409.
- Escalation is one atomic `UPDATE ... RETURNING` with `FOR UPDATE SKIP LOCKED`; a patient is escalated at most once and never from `EN_CONSULTA` or `ALTA`.
- Patient row, audit entry and outbox row are written in one transaction. Events go through the outbox (documented fallback: publish after commit with retry, recorded in an ADR).
- The audit table is append-only. There is no delete endpoint.

Assistant
- When retrieval finds no relevant excerpt, answer with the fixed notice and do **not** call the LLM.
- Sources in the response come from corpus metadata for validated ids, never from model free text. An invalid or unretrieved citation replaces the answer with the fixed notice.
- Circuit breaker, timeout and fallback HTTP 503 on LLM calls.

Isolation
- Triage never depends on the assistant. The urgency view is built only from Kafka events, never by calling the triage API.

Security and hygiene
- No secrets in code or git history. Configuration through environment variables (`GEMINI_API_KEY`, `JWT_PRIVATE_KEY`, and so on). Tests never call a real LLM.
- External contract in Spanish exactly as specified (paths, JSON fields, event names, state and level values). Code, comments, commits and docs in English. No `@author` tags.
- Specification ambiguities that change behavior get an ADR (for example ADR 0013).

## Working method

1. Read `docs/PLAN.md` and take the next unchecked task.
2. Write tests first for domain logic (classifier E1 to E6, transitions, SLA, complaint matching).
3. Keep commits small, with Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Commit only when the user asks. Follow `docs/GIT_WORKFLOW.md`: work branches from `develop`, changes reach `develop` and `main` only through pull requests, never run a local `git merge` into them.
4. Run `./gradlew build` before declaring a task done. Tick the box in `PLAN.md`.
5. Record any new ambiguity or decision in `docs/ASSUMPTIONS.md`; write an ADR for architecture-level decisions.

## Commands

Verified: `docker` exists on the dev machine. Everything else is not verified yet because no build has been run.

```bash
# JDK 21 (Temurin 21.0.10) is at ~/.jdks/temurin-21.0.10; the machine default is JDK 25.
# Gradle 8.14 may not run on 25, so run with:
export JAVA_HOME=$HOME/.jdks/temurin-21.0.10

./gradlew build                                   # compile and test all modules
./gradlew test                                    # tests only (unverified)
./gradlew :triaxis-triage-service:test            # one module (verified: passes, needs Docker running)
./gradlew :triaxis-triage-service:bootRun         # triage on 8081 (unverified)
./gradlew :triaxis-assistant-service:bootRun      # assistant on 8082 (unverified)
./gradlew :triaxis-auth-service:bootRun           # auth on 8083 (unverified)
docker compose up --build                         # whole system (compose file not created yet)
kubectl apply -f deploy/k8s/                      # manifests (not created yet; kubectl 1.34.13 and kind 0.33.0 are in ~/.local/bin; az is not installed)
```

JaCoCo reports are produced after `test` (root `build.gradle`).

## Stop and ask the user first

- Anything that changes the external contract (paths, JSON fields, event names, state or level values).
- Adding a dependency that is not in `TECH_STACK.md`, or adding a service or module.
- Anything that contradicts `ARCHITECTURE.md`, `TECH_STACK.md` or the requirements specification.
- Changes to the Azure plan or to anything that costs money.
