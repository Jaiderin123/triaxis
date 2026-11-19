# Triaxis: Implementation Plan

Working checklist. Follow the phases in order; tick boxes as work lands. Source of the order and scope: [`ARCHITECTURE.md`](ARCHITECTURE.md) section 15. Assumptions are logged in [`ASSUMPTIONS.md`](ASSUMPTIONS.md).

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

Tasks (tests first for the domain)
- [ ] `TriageClassifier` with one `TriageLevelRule` per level, `SlaPolicy`, administrative complaint normalization
- [ ] `StatusTransitionPolicy` (enum state machine)
- [ ] Init scripts for `patients` and `patient_audit` (append-only trigger), UTC session time zone
- [ ] `RegisterPatientUseCase`, `ChangePatientStatusUseCase`, `GetPatientUseCase` with conditional `UPDATE`
- [ ] ADR 0013 (state changes allowed to `NURSE` and `DOCTOR`) already drafted; keep it in sync with the security rules
- [ ] Functional endpoints: `POST /triage/pacientes`, `PATCH /triage/pacientes/{id}/estado`, `GET /triage/pacientes/{id}`
- [ ] `RequestValidator` (names the invalid field; decimals rejected for integer fields) and `GlobalErrorHandler` returning `ProblemDetail`
- [ ] Role rules in the `SecurityWebFilterChain`
- [ ] Metric `triage.classification` by level

Modules: `triaxis-triage-service`.

Tests that must exist: BlockHound proves no blocking call in the register flow; E1 to E6 and boundary values (HR 40 and 120, SpO2 89, 90, 93, 94, systolic 89, 90, 100, 101, temp 38.0 and 38.5, GCS 8, 9, 13, 14); valid and invalid transitions (409), unknown patient (404); validation for every range in the specification; concurrent `PATCH` (only one wins); audit written for each change.

Definition of done: `./gradlew :triaxis-triage-service:test` green, including Testcontainers PostgreSQL tests.

First commit: `feat(triage): classify and register patients with audited state flow`

## Phase 3: Events

**Goal:** every state change is published to `triaje.eventos` reliably.

Tasks
- [ ] Event records in `triaxis-common` (fields in Spanish, `version: 1`, headers `correlationId` and `eventType`)
- [ ] `outbox_events` table; patient, audit and outbox written in one transaction (`TransactionalOperator`)
- [ ] `OutboxRelay`: batch poll with `FOR UPDATE SKIP LOCKED`, single relay in flight, `acks=all`, idempotent producer, key = patient id
- [ ] Gauge `triage.outbox.pending`
- [ ] Fallback if short on time: publish after commit with retry (cut list item 5); record it in the ADR

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

