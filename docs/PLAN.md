# Triaxis: Implementation Plan

Working checklist. Follow the phases in order; tick boxes as work lands. Source of the order and scope: [`ARCHITECTURE.md`](ARCHITECTURE.md) section 15. Assumptions are logged in [`ASSUMPTIONS.md`](ASSUMPTIONS.md).

## Status

| Phase | State | Tag |
|---|---|---|
| 0 Skeleton | done | `phase-0-skeleton` |
| 1 Auth | done | `phase-1-auth` |
| 2 Triage core | done | `phase-2-triage-core` |
| 3 Events (outbox) | done | `phase-3-events` |
| 4 Escalation | next | |
| 5 Assistant | pending | |
| 6 Operation report | pending | |
| 7 Alerts | pending | |
| 8 Deployment | pending | |
| 9 Docs | pending | |

### Where to resume (phase 4)

Branch from `develop` and open pull requests as in `GIT_WORKFLOW.md`. Starting points for escalation:

- The atomic statement is in `ARCHITECTURE.md` section 6.6: one `UPDATE ... RETURNING` with `FOR UPDATE SKIP LOCKED` over `REGISTERED` patients whose SLA expired and that are not escalated.
- The audit entry (event type `ESCALATED`, same status before and after, actor `system`) and the outbox event `paciente_escalado` are written in the same transaction. `PatientEventType.ESCALATED`, `EventType.PACIENTE_ESCALADO` and the mapper entries already exist.
- The interval, the batch size and the SLA per level are properties (`triage.sla.minutes`, plus new `triage.escalation.*`), so tests can use seconds. Time comes from `ClockPort`.
- A patient who entered consultation or was discharged is never escalated, and a patient is escalated once (RN-03); the tests must cover the race with a concurrent `PATCH`.
- A scheduler adapter goes in `infrastructure.adapter.in.scheduler`, next to the outbox scheduler; BlockHound and ArchUnit tests must keep passing.
- Still to write: ADR 0006 (atomic escalation) and the ADRs of later phases.

## Cut list and priorities

The scope is large. When a phase takes longer than expected, apply cuts from the list below instead of dropping core requirements.

### Cut list (in order)

1. OpenTelemetry
2. CI workflow
3. Outbox (fall back to publish after commit with retry; record it in the ADR)
4. Email and SSE alternatives for alerts (documentation only)

### Never cut

- Tests for E1 to E6
- ArchUnit architecture tests and BlockHound non-blocking test (required, not optional)
- Escalation idempotency
- RAG citations and the no-match case
- Circuit breaker with 503
- Azure deployment
- README and ADRs

---

## Phase 0: Skeleton

**Goal:** a multi-module build where every service starts, plus local infrastructure.

**Status:** done (see the progress log at the end).

Tasks
- [x] Root `build.gradle`, `settings.gradle`, version catalog, wrapper, `.gitignore`, `README.md`
- [x] JDK 21 and Kubernetes tools (`kubectl`, `kind`) available locally
- [x] `docs/private/` holds the requirements specification and is in `.gitignore`
- [x] `git init`, first commit on `main`, create `develop`; set the repository-local `user.name` and `user.email` to the author's own identity; `.claude/` goes in `.git/info/exclude`
- [x] Create the remote repository, push `main` and `develop`, set branch protection (see `GIT_WORKFLOW.md`)
- [x] `triaxis-triage-service` fixed to Boot 3.5.16 (catalog plugins, Boot 3 starters, nested wrapper and `settings.gradle` removed, port 8081)
- [x] Create `triaxis-common`, `triaxis-security`, `triaxis-assistant-service`, `triaxis-auth-service` (build files, main classes, `application.yml` with port and name only) and include them in `settings.gradle`
- [x] `docker-compose.yml`: PostgreSQL with pgvector (two databases through an init script), Kafka (KRaft, topic `triaje.eventos` with 3 partitions), Redis
- [x] Add ArchUnit 1.5.1 and BlockHound 1.0.18.RELEASE to the version catalog and test dependencies of the services (see `TECH_STACK.md`)
- [x] Actuator liveness and readiness on every service
- [x] `docs/adr/README.md` and `docs/adr/0000-template.md`

Files and modules: root build files, all five modules, `docker-compose.yml`, `docs/adr/`.

Tests: one context-load test per service; an ArchUnit test per service enforcing the layer rules; a BlockHound test in triage and assistant.

Definition of done: `./gradlew build` passes and `docker compose up -d` starts PostgreSQL, Kafka and Redis healthy.

First commit: `chore: add multi-module skeleton and local infrastructure`

## Phase 1: Auth

**Goal:** tokens can be issued and validated by the other services.

Decisions (approved): OAuth2 field names (`username`, `password`, `access_token`, `token_type`, `expires_in`); demonstration users `nurse` and `doctor` in the local profile with BCrypt hashes; the signing key is generated only with `auth.jwt.generate-key-if-missing=true`, otherwise `JWT_PRIVATE_KEY` is required; `auth-service` does not depend on `triaxis-security` (only a test-scope dependency for the integration test). See ADR 0003 and ASSUMPTIONS A18.

Work is split into two pull requests: the shared security module (`feat/security-resource-server`) and the auth service with token endpoint and JWKS (`feat/auth-token-jwks`), which were merged into one because the integration test needs both.

Tasks
- [x] `triaxis-security`: auto-configuration with a lazy JWKS decoder (RS256, issuer and expiration checks), `roles` to `ROLE_*` converter, public health probes, `AuthorizationRulesCustomizer` hook for per-service rules (`feat/security-resource-server`)
- [x] `triaxis-triage-service` and `triaxis-assistant-service` set `triaxis.security.jwks-uri` (`AUTH_JWKS_URI`, default `http://localhost:8083/.well-known/jwks.json`)
- [x] `POST /auth/token` with users and roles (`NURSE`, `DOCTOR`) from configuration, BCrypt hashes 
- [x] RS256 signing, 15-minute tokens with `sub`, `roles`, `iss`, `iat`, `exp`, `jti` and `kid`; key from `JWT_PRIVATE_KEY`
- [x] `GET /.well-known/jwks.json` and an integration test where the decoder of `triaxis-security` validates a token issued by auth 
- [x] Auth security chain: only `/auth/token`, JWKS and health probes are public
- [x] ADR 0003

Modules: `triaxis-auth-service`, `triaxis-security`.

Tests: token issue and rejection of bad credentials; JWKS exposes the matching public key; expired token, wrong issuer, wrong key and tampered payload give 401; role rule gives 403; ArchUnit and context load.

Definition of done: `./gradlew build`; the integration test validates an auth-issued token with the shared decoder. Checking a token against a real triage endpoint happens in phase 2, when triage has protected routes.

First commit: `feat(auth): issue RS256 tokens and expose JWKS`

## Phase 2: Triage core

**Goal:** RF-01, RF-02, RN-01, RN-02, RN-04 working end to end without Kafka.

Decisions (approved): validation lives in the domain and reports every violation with its field name (the web layer only rejects malformed JSON and wrong types; decimals are rejected by turning off `accept-float-as-int`); database `CHECK` constraints as a safety net; boundary mappers use MapStruct 1.6.3 (ADR 0015) and only copy data; domain enums carry no external values, mappers convert `CRITICO`, `REGISTRADO` and so on; audit values are English enums and the audit stores the actor (JWT `sub`, or `system`) (ADR 0014); `PATCH` returns 200 with the full patient; the JSON never mixes languages (`fechaCambioEstado`, `tiempoEnEstadoSegundos`). See ASSUMPTIONS A22 to A25.

Split into four pull requests: domain, application, persistence, API.

Persistence notes: the schema is one idempotent script (`db/schema.sql`) that Spring Boot runs at startup as a single statement (custom `spring.sql.init.separator`), so the trigger function body is not split; the audit table references `patients` through a foreign key; database `CHECK` constraints mirror the domain ranges; connection settings come from `TRIAGE_DB_URL`, `TRIAGE_DB_USER` and `TRIAGE_DB_PASSWORD` (the `local` profile has the compose defaults); instants are stored as `TIMESTAMPTZ` and mapped through `OffsetDateTime` in UTC.

Tasks
- [x] Domain (`feat/triage-domain`): `TriageLevel`, `PatientStatus` with its transitions, `VitalSigns`, `RegistrationData`, `Patient`, `AuditEntry` and its event types, `TriageLevelRule` (one class per level), `TriageClassifier`, `ComplaintPolicy` (normalized exact match, configurable list), `SlaPolicy`, `StatusTransitionPolicy`, validation with field violations, domain exceptions
- [x] Application (`feat/triage-application`): `RegisterPatientUseCase`, `ChangePatientStatusUseCase`, `GetPatientUseCase` (actor as an argument), output ports `PatientRepository`, `AuditRepository`, `ClockPort`, `TriageMetrics`, `UnitOfWork`; tests with fake ports
- [x] Persistence (`feat/triage-persistence`): idempotent schema script run at startup (`patients`, `patient_audit` with the actor and an append-only trigger, `CHECK` constraints, index), R2DBC adapters, conditional `UPDATE ... RETURNING`, transaction adapter, MapStruct entity mappers; add MapStruct to the version catalog
- [x] API (`feat/triage-api`): `POST /triage/pacientes`, `PATCH /triage/pacientes/{id}/estado`, `GET /triage/pacientes/{id}`, request validation of JSON shape, MapStruct DTO mappers, `GlobalErrorHandler` (`ProblemDetail`: 400 naming the field, 404, 409), role rules through `AuthorizationRulesCustomizer`, metric `triage.classification`
- [x] Manual end-to-end check: compose infrastructure, auth and triage running, a token from auth used on triage

Modules: `triaxis-triage-service`.

Tests that must exist: BlockHound proves no blocking call in the register flow; E1 to E6 and boundary values (HR 40 and 120, SpO2 89, 90, 93 and 94, systolic 89, 90, 100 and 101, temperature 38.0 and 38.5, GCS 8, 9, 13 and 14); complaint normalization; all nine transitions; every range of RF-01 including diastolic below systolic; concurrent `PATCH` (only one wins); audit rejects `UPDATE` and `DELETE`; schema script runs twice; 401, 403 and 200 per role; a real signed token accepted; ArchUnit.

Definition of done: `./gradlew :triaxis-triage-service:test` green, including Testcontainers PostgreSQL tests, plus the manual end-to-end check.

First commit: `feat(triage): classify and register patients with audited state flow`

## Phase 3: Events

**Goal:** every state change is published to `triaje.eventos` reliably.

Decisions (approved): full transactional outbox (not the publish-after-commit fallback); the relay lives inside `triaxis-triage-service` as an input adapter (scheduler); a `correlationId` is generated per request, stored in the outbox row and sent as a Kafka header. Domain events go through an `EventPublisher` port and the adapter maps them to the contract with MapStruct. Planned pull requests: contract, outbox writing, relay with producer and gauge, then docs (ADR 0001 and 0005).

Tasks
- [x] Event records in `triaxis-common` (`TriageEvent`, `EventType`, `EventLevel`, `EventStatus`; fields in Spanish, `version: 1`; topic and header names as constants) (`feat/events-contract`)
- [x] `outbox_events` table (with `correlation_id`); patient, audit and outbox written in one transaction; `EventPublisher` port, domain `PatientEvent`, MapStruct mapper to the contract; `X-Correlation-Id` filter feeding the outbox (`feat/triage-outbox`)
- [x] `OutboxRelay`: batch poll with `FOR UPDATE SKIP LOCKED`, advisory lock (single relay in flight), `acks=all`, idempotent producer, key = patient id, headers `eventType` and `correlationId`; scheduler loop (`feat/triage-outbox-relay`)
- [x] Gauge `triage.outbox.pending` plus counters `triage.outbox.published` and `triage.outbox.failures`
- [x] Fallback not needed: the full outbox was built (ADR 0005); ADR 0001 explains Spring Kafka

Modules: `triaxis-common`, `triaxis-triage-service`.

Tests: event emitted per transition with the right type and level; relay marks rows published; an event is not lost when Kafka is down at commit time (Testcontainers Kafka).

Definition of done: `./gradlew :triaxis-triage-service:test`; with compose running, a registration appears on the topic (`kafka-console-consumer`, command not verified).

First commit: `feat(triage): publish patient events through a transactional outbox`

## Phase 4: Escalation

**Goal:** RF-03 and RN-03.

Tasks
- [ ] `EscalationScheduler` with `triage.escalation.interval`, `batch-size` and SLA per level as properties
- [ ] Atomic `UPDATE ... RETURNING` with `SKIP LOCKED`; audit and outbox rows in the same transaction
- [ ] Metric `triage.escalations`

Modules: `triaxis-triage-service`.

Tests: overdue patient escalated once; second run is a no-op (no second event, no second audit row); `EN_CONSULTA` and `ALTA` patients never escalated; two concurrent runs produce one escalation; race between `PATCH` and scheduler has a single winner. Use second-level SLAs and a fake `Clock`.

Definition of done: `./gradlew :triaxis-triage-service:test --tests '*Escalation*'`.

First commit: `feat(triage): escalate overdue patients atomically and idempotently`

## Phase 5: Assistant

**Goal:** RF-04 A and B, RN-05, resilience.

Tasks
- [ ] Protocol files for PROTO-001 to PROTO-003 in `src/main/resources/protocols/`; idempotent ingestion (content hash and embedding model)
- [ ] `protocol_chunks` with untyped `vector` column; custom R2DBC retrieval with `<=>`
- [ ] `LlmPort` with Gemini adapter and `mock` adapter (deterministic embeddings and answers); `mock` is the default profile
- [ ] `AskClinicalQuestionUseCase`: embed, retrieve, threshold gate (no LLM call below it), prompt with numbered excerpts, structured output with excerpt ids or `NO_PROTOCOL`, citation validation, sources from corpus metadata
- [ ] `ResilientClinicalAnswerGenerator` decorator: circuit breaker, time limiter, 503 fallback
- [ ] Kafka listener (group `assistant-urgency-view`, manual ack) with Lua script applying events idempotently to Redis
- [ ] `GET /asistente/estado-urgencias` returns all five levels and a total
- [ ] Metrics `assistant.llm.latency`, `assistant.answers` by outcome
- [ ] Calibrate `assistant.rag.max-distance` with the three sample questions

Modules: `triaxis-assistant-service`.

Tests that must exist: sample questions 1 and 2 cite PROTO-001 and PROTO-002; question 3 (diabetes) returns the fixed notice with empty sources and the fake LLM is never invoked; invalid or unretrieved citation is rejected; breaker opens after failures and the endpoint answers 503; urgency view counts correct with duplicate events and out-of-order discharge; Redis view rebuilt from replay.

Definition of done: `./gradlew :triaxis-assistant-service:test`.

First commit: `feat(assistant): add RAG with validated citations and urgency view`

## Phase 6: Operation report

**Goal:** RF-05.

Tasks
- [ ] `ReportQuery` port and R2DBC adapter with the SQL in `ARCHITECTURE.md` 6.7 (`FILTER`, `percentile_cont`, `sum() OVER`, `rank() OVER`)
- [ ] `GET /triage/reportes/operacion?desde=&hasta=` (DOCTOR), 400 for invalid or inverted range

Tests: Testcontainers dataset with known waits asserting counts per level, average, p95, cumulative total and rank; discharged-directly patients excluded from waits; empty range.

Definition of done: `./gradlew :triaxis-triage-service:test --tests '*Report*'`.

First commit: `feat(triage): add hourly operation report with window functions`

## Phase 7: Alerts, RF-06

**Goal:** the medical team is alerted when a patient is escalated.

Tasks
- [ ] `AlertNotifier` port; `LoggingAlertNotifier` (default) and `WebhookAlertNotifier` (configurable URL)
- [ ] Kafka listener in its own consumer group for `paciente_escalado`, retry with backoff, failure counted and never propagated
- [ ] ADR 0011 (alert channel)

Tests: escalated event triggers one notification; webhook failure is retried and counted; fake webhook server receives the JSON.

Definition of done: `./gradlew :triaxis-triage-service:test --tests '*Alert*'`.

First commit: `feat(triage): notify the medical team of escalated patients`

## Phase 8: Deployment

**Goal:** one-command local run, Kubernetes manifests, a reachable Azure deployment.

Tasks
- [ ] One Dockerfile per service (pinned base image tags, no `latest`)
- [ ] `docker-compose.yml` runs infrastructure plus the three services; `mock` LLM by default
- [ ] `deploy/k8s/`: namespace, Deployments with probes and resources, Services, ConfigMap, Secrets, Ingress with `/auth`, `/triage`, `/asistente`; PostgreSQL, Kafka, Redis single replica
- [ ] Validate on kind or minikube (not installed on the dev machine yet)
- [ ] Azure: choose AKS or Container Apps (ADR 0010), set a budget alert, **an early smoke deploy**, record the URL
- [ ] Decide which LLM profile the deployed environment uses (ASSUMPTIONS A16)

Definition of done: `docker compose up --build` brings the system up and the sample flow works with curl; `kubectl apply -f deploy/k8s/` works on the local cluster; the Azure URL answers `/actuator/health/readiness`.

First commit: `build: add Dockerfiles, compose and Kubernetes manifests`

## Phase 9: Docs

**Goal:** README and ADRs ready for a reader who has never seen the project.

Tasks
- [ ] README: one-command run, architecture diagram, stack, API overview, decisions, testing, deployment, author
- [ ] ADRs: at least 0001, 0002, 0006, 0007 (priority), then the rest of the plan in `ARCHITECTURE.md` section 14
- [ ] Link `ASSUMPTIONS.md` from the README; confirm the assumptions still match the code
- [ ] Final check that no secret or private file is in the repository or its history

Definition of done: a fresh clone runs with `docker compose up --build` following only the README.

First commit: `docs: complete README and architecture decision records`

---

## Final checklist (deliverables of the specification)

- [ ] Source repository with the complete code (public, or private with access for the reviewer)
- [ ] README with a one-command local run, an architecture diagram, technologies and key decisions with justification
- [ ] Automated unit and integration tests covering: E1 to E6, valid and invalid transitions, escalation idempotency, RAG citations, no-match without hallucination
- [ ] Deployed URL on Azure with the APIs reachable, plus the Kubernetes manifests used
- [ ] At least two ADRs (target: four or more)
- [ ] `docker-compose` starts PostgreSQL, Kafka, Redis and the services with one command
- [ ] No hardcoded credentials; LLM key only through environment variable
- [ ] Observability: probes, structured logs with correlation id, Micrometer metrics
- [ ] Iterative commit history with working versions

## Traceability

| Requirement | Phase |
|---|---|
| RF-01 Register and classify | 2 (event in 3) |
| RF-02 State flow | 2 (events in 3) |
| RF-03 SLA and escalation | 4 |
| RF-04 A Assistant (RAG) | 5 |
| RF-04 B Urgency view | 5 (depends on events from 3) |
| RF-05 Operation report | 6 |
| RF-06 Alerts | 7 |
| RN-01 Classification | 2 |
| RN-02 SLA per level | 2 (used in 4) |
| RN-03 Escalation idempotency | 4 |
| RN-04 Audit | 2 (escalation rows in 4) |
| RN-05 Anti-hallucination | 5 |

---

## Progress log

One entry per finished piece of work: what was done, decisions taken, how it was verified, and the pull requests. Keep it factual so the plan works as a living specification.

### Phase 0: Skeleton (done)

- **What:** five-module Gradle build (Boot 3.5.16, Java 21 toolchain, JaCoCo), wrapper 8.14.7, version catalog (Boot, dependency-management plugin, Spring AI 1.1.8 BOM, Resilience4j 2.4.0, ArchUnit 1.5.1, BlockHound 1.0.18.RELEASE), `docker-compose.yml` with PostgreSQL (pgvector, databases `triage` and `assistant`), Kafka KRaft (topic `triaje.eventos`, 3 partitions) and Redis.
- **Decisions:**
  - Triage application class renamed to `TriageApplication` to match the architecture; test containers moved to Testcontainers 1.x (the version Boot 3.5 manages) with pinned images (`postgres:16-alpine`, `apache/kafka:3.9.1`).
  - Spring AI Google GenAI starters left as a TODO for phase 5: their auto-configuration needs an API key and breaks the context test. Both artifacts exist in 1.1.8.
  - Health probes enabled in every service. They are still behind the default security (401) until phase 1 makes them public.
  - The `-XX:+AllowRedefinitionToAddDeleteMethods` flag is set for tests so BlockHound works on JDK 21 (it prints a deprecation warning, harmless).
- **Verified:** `./gradlew build` passes with JDK 21; `docker compose up -d` brings PostgreSQL, Kafka and Redis healthy, both databases and the `vector` extension exist, and the topic has 3 partitions.
- **PRs:** #1 (README author), #2 (modules), #3 (local infrastructure), all squash-merged into `develop`.
- **Environment note:** Fedora 44 has no JDK 21 package; Temurin 21 lives in `~/.jdks`, and Gradle needs `JAVA_HOME` pointing to it. `kubectl` and `kind` are installed in `~/.local/bin`.
- **Next:** merge `develop` into `main` through a PR and tag `phase-0-skeleton`, then start phase 1.

### Phase 1: Auth (done)

- **What:** `triaxis-security` auto-configuration (lazy JWKS decoder with RS256, issuer and expiration validation; `roles` to `ROLE_*`; public health probes; `AuthorizationRulesCustomizer` hook for per-service rules; `triaxis.security.enabled` switch). `triaxis-auth-service`: hexagonal layout, `POST /auth/token`, `GET /.well-known/jwks.json`, config-backed users with BCrypt, RS256 signing with `kid`, `ProblemDetail` error handler, security chain that only exposes the token endpoint, JWKS and health probes.
- **Decisions:** OAuth2 field names; `local` profile holds the demonstration users (`nurse` / `nurse-demo`, `doctor` / `doctor-demo`) and enables key generation; without `JWT_PRIVATE_KEY` and without that flag the service refuses to start; password check runs on `boundedElastic` and compares against a dummy hash for unknown users; auth has a test-scope dependency on `triaxis-security` only for the integration test, with `triaxis.security.enabled=false` in its test properties; the two auth PRs were merged into one. See ADR 0003 and ASSUMPTIONS A18.
- **Verified:** `./gradlew build` passes (security: 11 tests; auth: 12 tests, including a real HTTP test where the shared decoder validates an auth-issued token through the JWKS endpoint, token rejection cases, and public-key-only JWKS).
- **PEM key:** `JWT_PRIVATE_KEY` loading is covered by `SigningKeyProviderTest` (PKCS#8 PEM, one-line form with escaped newlines, same key id across restarts, token verified against the published JWKS, refusal to start without a key, invalid PEM).
- **Not verified yet:** a token against a real triage endpoint (triage has no protected routes until phase 2).
- **Run locally:** `./gradlew :triaxis-auth-service:bootRun --args='--spring.profiles.active=local'`.

### Phase 2: Triage core (done)

- **What:** domain (levels, statuses, rules per level, classifier, complaint, SLA and transition policies, validation with field violations), application (register, change status, get; actor as argument; unit of work), persistence (idempotent schema with `CHECK` constraints, audit with actor and append-only trigger, R2DBC repositories, conditional `UPDATE ... RETURNING`, MapStruct mappers) and API (`POST /triage/pacientes`, `PATCH /triage/pacientes/{id}/estado`, `GET /triage/pacientes/{id}`, `ProblemDetail` errors with the external field names, role rules, metric `triage.classification`).
- **Decisions:** the four pull requests were #10 (domain), #11 (application), #12 (persistence) and the API one; JSON field aliases for the registration body (`nombre`, `fc`, `SpO2`, `queja`, ...); strict JSON (decimals and strings are rejected for integer fields); the 409 message uses the external status names; error bodies are standard `ProblemDetail` (English text, Spanish field names, `errores` list with `campo` and `mensaje`).
- **Found by BlockHound:** `UUID.randomUUID()` reads the system entropy source, a blocking call, and it ran on a `parallel` thread. Id generation is now an `IdGenerator` port returning `Mono<UUID>`, implemented on `boundedElastic`. The register, change-status and read flow runs under BlockHound without errors.
- **Verified:** `./gradlew build` passes on JDK 21 (domain, application, persistence with Testcontainers, API, security and non-blocking flow; three consecutive runs stable). Manual end-to-end check with the compose database and both services running: a token from auth (`nurse` and `doctor`) is accepted by triage; no token gives 401, a doctor registering gives 403, a doctor changing status works, an invalid transition gives 409, an invalid field gives 400 naming it, and the audit rows carry the right actors.
- **Not verified yet:** events to Kafka (phase 3), escalation (phase 4), operation report (phase 6).
- **Test infrastructure:** all integration tests share one annotation (`@TriageIntegrationTest`: real PostgreSQL and Kafka containers plus a test JWT decoder) so Spring reuses a single context; the web test client timeout is 30 seconds.

### Phase 3: Events (done)

- **What:** event contract in `triaxis-common` (`TriageEvent`, `EventType`, `EventLevel`, `EventStatus`, topic and header names); domain event `PatientEvent` and the `EventPublisher` port; `outbox_events` table written in the same unit of work as the patient and the audit entry; MapStruct mapper to the Spanish contract; `X-Correlation-Id` filter feeding the outbox; `OutboxRelay` (advisory lock, `FOR UPDATE SKIP LOCKED`, ordered sends, mark after acknowledgment, stop on the first failure) with a scheduler loop; producer with `acks=all` and idempotence; the topic is created at startup when missing; gauge and counters.
- **Decisions:** full transactional outbox instead of the fallback (ADR 0005); Spring Kafka (ADR 0001); the relay lives in the triage service; correlation id per request, copied to the Kafka header; the relay loop is switched off in tests (`triage.outbox.relay.enabled=false`) and tests call `relayOnce()` so they are deterministic; the pending gauge is refreshed by the relay and never queries the database from the metrics thread.
- **Verified:** `./gradlew build` passes on JDK 21 with real Kafka and PostgreSQL containers: contract serialization, one event per transition with the right type and level, rollback of the event with the unit of work, delivery with key, headers and payload, order per patient, one batch per cycle, no loss when Kafka is unreachable (the event stays pending and is delivered later), two relays at once send each event once, the periodic loop delivers and stops, the topic has three partitions, and the register flow is still clean under BlockHound.
- **Manual check:** with the compose PostgreSQL and Kafka and both services running, a registration and a status change produced two messages on `triaje.eventos` (read with `kafka-console-consumer`) with the patient id as key, the headers `eventType` and `correlationId` (the one sent by the caller, or a generated one), the contract payload, and both outbox rows marked as published.
- **Not verified yet:** a consumer built on these events (phase 5, the assistant) and the behavior of the relay across several replicas on a real cluster.

---

## Improvements (out of scope for now)

Ideas that were consciously left out of the first delivery:

- Read endpoint for the clinical audit (`GET /triage/pacientes/{id}/auditoria`); the audit is written and protected, but only readable through the database.
- Custom `ProblemDetail` bodies for 401 and 403 responses coming from Spring Security.
- Key rotation procedure for the JWT signing key and token revocation.
