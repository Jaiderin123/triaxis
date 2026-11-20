# Triaxis: Implementation Plan

Working checklist. Follow the phases in order; tick boxes as work lands. Source of the order and scope: [`ARCHITECTURE.md`](ARCHITECTURE.md) section 15. Assumptions are logged in [`ASSUMPTIONS.md`](ASSUMPTIONS.md).

## Status

| Phase | State | Tag |
|---|---|---|
| 0 Skeleton | done | `phase-0-skeleton` |
| 1 Auth | done | `phase-1-auth` |
| 2 Triage core | done | `phase-2-triage-core` |
| 3 Events (outbox) | done | `phase-3-events` |
| Platform foundation (errors, observability, hardening) | done | `platform-foundation` |
| 4 Escalation | done | `phase-4-escalation` |
| 5 Assistant | done | `phase-5-assistant` |
| Review fixes of 2026-11-19 (between phases 5 and 6) | done | `review-2026-11-19` |
| 6 Operation report | next (planned) | |
| 7 Alerts | pending | |
| 8 Deployment | pending | |
| 9 Docs | pending | |

### Definition of done for phases 6 to 9 (engineering rules of the review of 2026-11-19)

Every pull request of phases 6 to 9 checks, in addition to the build and `scripts/check-docs.sh` (the rules are in `CLAUDE.md`):

- [ ] Every remote call and every wait has an explicit timeout; every background loop has a per-cycle timeout.
- [ ] Time handling in the database and in SQL is UTC; the test JVM runs in a non-UTC zone.
- [ ] No model output, patient data, question or secret is logged; records holding secrets override `toString`.
- [ ] Every new route has its explicit rule and a test per role (the route rule test passes).
- [ ] Every new configuration property is validated and documented in `ARCHITECTURE.md` section 10.
- [ ] A task is ticked only after it was verified; a decision changed in the code updates or supersedes its ADR in the same pull request.
- [ ] Concurrency tests force the interleavings they claim; no `Thread.sleep` to synchronize.
- [ ] Read models and markers use keys of their own; the replica assumptions of a background process are stated.
- [ ] A fresh clone of the branch compiles.

### Where to resume (phase 6)

Phases 4 and 5 are released to `main` (tags `phase-4-escalation` and `phase-5-assistant`), and so are the fixes of the review of 2026-11-19 (tag `review-2026-11-19`). Next is phase 6, the operation report (RF-05):

- The phase is planned: the approved decisions are in the Phase 6 section below (A41 to A43). Start with `feat/phase-6-report` from `develop`. The SQL is in `ARCHITECTURE.md` section 6.7; A7, A8 and A12 also apply.
- One branch from `develop` and one pull request, rebase and merge; then the release pull request to `main` (merge commit) and the tag.
- Conventions that now apply to every service: the definition of done above; a source package named `out` is tracked (the ignore rule only covers `/out/` and `/*/out/`); in the assistant, BlockHound is installed when the test JVM starts; errors through the shared base handler and writer; the security chain denies every route without its rule; database sessions are UTC; no secrets in the repository.
- For the report: `date_trunc('hour', ...)` gives UTC hours because the session is UTC (ADR 0022, tested in `SessionTimeZoneTest`); the endpoint is `DOCTOR` only and needs its rule in `SecurityRulesConfiguration`.
- Documentation sync: follow the checklist in `CLAUDE.md` and run `scripts/check-docs.sh` before each pull request.

## Cut list and priorities

The scope is large. When a phase takes longer than expected, apply cuts from the list below instead of dropping core requirements.

### Cut list (in order)

1. CI workflow
2. Email and SSE alternatives for alerts (documentation only)

(OpenTelemetry and the outbox were built in full and are no longer candidates.)

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
- [x] Create the remote repository, push `main` and `develop`
- [x] Branch protection on `main` and `develop`: pull request required (no approvals), no direct pushes or force-pushes (admins included), no deletion, no required status checks because there is no CI (see `GIT_WORKFLOW.md`). It was marked done here by mistake, found missing in the review of 2026-11-19 and applied on 2026-11-20 (checked with `gh api repos/Jaiderin123/triaxis/branches/<branch>/protection`)
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

Decisions (approved): one branch `feat/phase-4-escalation` and one pull request for the whole phase, with a few logical commits; scheduler interval of 30 seconds by default (`triage.escalation.interval`, 1 second in tests), batch size 100, loop repeated at once when a batch is full; the actor of the audit entries is `system`; the `GET` of a patient does not get a new field (the `escalado` flag already exists); an escalated patient stays `REGISTRADO` and `status_changed_at` does not change; SLA expiry is strictly greater (A6); counter `triage.escalations` tagged by level; tag `phase-4-escalation`.

Tasks
- [x] Domain event `PatientEvent.escalated`, audit entry of type `ESCALATED` (same status before and after, `escalated = true`), mapper entry (the enum values already exist)
- [x] `PatientRepository.escalateOverdue(now, batch)`: one atomic `UPDATE ... RETURNING` with `FOR UPDATE SKIP LOCKED` (`ARCHITECTURE.md` 6.6)
- [x] `EscalateOverduePatientsUseCase`: runs the update and, for every escalated patient, writes the audit entry and the outbox event in the same unit of work
- [x] `EscalationScheduler` (`infrastructure.adapter.in.scheduler`) with `triage.escalation.enabled`, `interval` and `batch-size` as properties; switched off in tests, which call the use case
- [x] Metric `triage.escalations` by level
- [x] ADR 0006 (atomic escalation), architecture and plan updated

Modules: `triaxis-triage-service`.

Tests that must exist: an overdue patient is escalated once and a second run does nothing (no second event, no second audit row); `EN_CONSULTA`, `ALTA` and already escalated patients are never selected; the limit is strict (exactly at the SLA is not escalated, one instant later is); oldest first and the batch size is respected; patient, audit and outbox event are committed together and rolled back together; two concurrent runs escalate each patient once; 25 rounds of a concurrent `PATCH` and escalation keep the invariant that an `ESCALATED` audit entry never follows a `CONSULTATION_STARTED` or `DISCHARGED` one; the event reaches Kafka with the level and `escalado = true`; the counter increments; BlockHound and ArchUnit stay green. Use a fake clock or back-dated patients, never real waiting.

Definition of done: `./gradlew build` passes, `scripts/check-docs.sh` passes, and a manual check with the compose infrastructure and an SLA of 1 minute shows the escalation in the audit, the outbox and Kafka.

Suggested commits inside the branch: domain and application; persistence and scheduler; tests and documentation.

## Phase 5: Assistant

**Goal:** RF-04 A and B, RN-05, resilience.

Decisions (approved, not started yet):
- Two branches and two pull requests into `develop`: `feat/assistant-rag` (corpus, pgvector, LLM, anti-hallucination, circuit breaker) and then `feat/assistant-urgency-view` (Kafka and Redis). The release to `main` and the tag `phase-5-assistant` come after both.
- No Gemini key yet: everything is built and tested with the `mock` provider (the default); the Gemini adapter is built and tested with fakes, and its threshold is calibrated later (ASSUMPTIONS A36, A16).
- Gemini through the Spring AI 1.1.8 starters `spring-ai-starter-model-google-genai` and `spring-ai-starter-model-google-genai-embedding` (checked on Maven Central); with `mock` their auto-configuration is switched off with `spring.ai.model.chat=none` and `spring.ai.model.embedding.text=none`. Models are properties.
- Corpus: one Markdown file per protocol, one chunk per section, id `PROTO-001#medidas-iniciales`; `seccion` is the section title of the corpus. Idempotent ingestion at startup (content hash and embedding model).
- Retrieval: top-k 4 by cosine distance over R2DBC (`<=>`); only chunks within `max-distance` reach the model; none within it gives the fixed notice and no LLM call.
- The model returns JSON with the answer and the excerpt numbers used, or `NO_PROTOCOL`; an invalid or unretrieved citation gives the fixed notice; sources come from corpus metadata only. Mock answer is extractive.
- Fixed notice `No existe un protocolo disponible para esta pregunta.` (200, `fuentes: []`); `pregunta` required, at most 1000 characters (A34).
- One circuit breaker `llm` over the embedding and the chat call, explicit timeouts, no retry of the chat call, 503 through the shared `ProblemWriter`.
- Urgency view with the robust rule of ADR 0021 (A33 replaces A19): any non-discharge event registers an unknown patient, discharge is final with a 30-day marker; rebuild from the earliest offset when `urgency:meta` is missing.
- Response of the view: `porNivel` with the five levels, `total`, `actualizadoEn` (A35).
- Roles: `POST /asistente/consulta` is `DOCTOR`; `GET /asistente/estado-urgencias` is `NURSE` and `DOCTOR`.
- Readiness: database and Redis, not Kafka and not the LLM.
- Test containers: `pgvector/pgvector:pg16` (the same image as compose) and `redis:7.4-alpine`, through Testcontainers generic containers; no new test library.

Pull request 1: RAG (`feat/assistant-rag`)
- [x] Protocol files PROTO-001 to PROTO-003 in `src/main/resources/protocols/`; parser into sections
- [x] Schema `protocol_chunks` (untyped `vector`), idempotent ingestion; custom R2DBC repository with `<=>` (ADR 0002)
- [x] `LlmPort` (embedding and structured answer); `mock` adapter (default) and Gemini adapter with the starters; blocking calls on `boundedElastic`
- [x] `AskClinicalQuestionUseCase`: embed, retrieve, threshold gate, numbered prompt, structured output, citation validation, sources from metadata (ADR 0007)
- [x] `ResilientLlmAdapter` decorator: circuit breaker, timeouts, 503 with `Retry-After` (ADR 0004)
- [x] `POST /asistente/consulta` (functional endpoint, MapStruct DTO mapper, global error handler on the shared base, role rule)
- [x] Metrics `assistant.llm.latency`, `assistant.answers` by outcome (`answered`, `no_protocol`, `rejected`, `degraded`), breaker state
- [x] Calibrate `assistant.rag.max-distance` of the `mock` provider with the three sample questions (0.70)
- [x] ADR 0008 (Gemini, `mock`, discarded providers)
- [x] With a real Gemini key (added during the phase): models checked, threshold calibrated (0.33), end-to-end check through the endpoint (A36)

Pull request 2: urgency view (`feat/assistant-urgency-view`)
- [x] Kafka listener (group `assistant-urgency-view`, manual acknowledgment after the script), Lua script with the rules of ADR 0021
- [x] Rebuild from the earliest offset when `urgency:meta` is missing (when partitions are assigned, A38)
- [x] `GET /asistente/estado-urgencias` with the five levels, the total and `actualizadoEn`
- [x] Readiness with Redis; consumer side of the `traceparent` header (optional `correlationId`, A31)

Modules: `triaxis-assistant-service`.

Tests that must exist: sample questions 1 and 2 answer from and cite PROTO-001 and PROTO-002; question 3 (diabetes) returns the fixed notice with empty sources and the fake LLM is never invoked; an invalid or unretrieved citation and `NO_PROTOCOL` give the fixed notice; sources never come from model text; ingestion runs twice without duplicates and re-embeds on a model change; the breaker opens after failures and the endpoint answers 503, and a timeout counts as a failure; blank and too long questions give 400; 401, 403 and 200 per role; urgency view counts right with duplicate events, a missing registration, a discharge before the registration and a full replay; all five levels with zeros; BlockHound over the question flow and the listener; ArchUnit.

Definition of done: `./gradlew build` passes, `scripts/check-docs.sh` passes, and a manual check with the compose infrastructure shows the three sample questions answered as expected with the `mock` provider and the urgency view following registrations and discharges made through triage.

## Phase 6: Operation report

**Goal:** RF-05.

Decisions (approved, not started yet):
- One branch `feat/phase-6-report` from `develop`, one pull request (rebase and merge), then the release to `main` and the tag `phase-6-report`.
- `GET /triage/reportes/operacion?desde=&hasta=`, `DOCTOR` only. The response is an object with `desde`, `hasta` and `horas` (A41).
- `desde` and `hasta` are required; each is a date (a whole UTC day) or an instant with a zone; a date-time without a zone is 400; the range is half-open, not empty, at most 31 days (`triage.report.max-range`) (A42).
- Hours without patients are omitted (A8). Buckets are UTC hours by registration; waits only count patients who entered consultation, `null` when none did; minutes with 2 decimals; `rank()` with shared ranks for ties (A43).
- One SQL query as in `ARCHITECTURE.md` 6.7 (`FILTER`, `percentile_cont`, `sum() OVER`, `rank() OVER`), never aggregated in memory; a new index on `patients (registered_at)`.
- Hexagonal: domain `ReportRange` (validation) and `HourlyLoad`; `GetOperationReportUseCase`; port `ReportQuery`; R2DBC adapter; functional endpoint with a MapStruct mapper; the role rule in `SecurityRulesConfiguration`.

Tasks
- [ ] Domain: `ReportRange` (half-open, not empty, maximum length, field violations) and `HourlyLoad`; unit tests first
- [ ] Application: `GetOperationReportUseCase` and the `ReportQuery` port; test with a fake query
- [ ] Persistence: the report SQL in an R2DBC adapter, the index on `registered_at`, the mapping of `numeric` and `null` waits
- [ ] API: `GET /triage/reportes/operacion` (parsing of dates and instants with zone, 400 naming the field), DTOs in Spanish, MapStruct mapper, role rule
- [ ] `triage.report.max-range` validated and documented in `ARCHITECTURE.md` section 10
- [ ] Documentation and assumptions updated; manual check with the compose database

Tests that must exist:
- A Testcontainers dataset in a past epoch of its own, with known registrations and waits, that checks per hour the total, the counts per level, the average and the p95.
- The running total, and the rank with a tie.
- Patients discharged directly and still waiting are counted in the totals and excluded from the waits; an hour with no consultation has `null` waits.
- An empty range gives an empty list, `hasta` is excluded, a whole day from date parameters, an instant with an offset.
- 400 for missing, malformed, zone-less, inverted, empty and longer than 31 days ranges, naming the field.
- 401 without a token, 403 for `NURSE`, 200 for `DOCTOR`, and the route rule test passes.
- Buckets are UTC with the test JVM in `America/Bogota`, and the report flow under BlockHound.

Definition of done: `./gradlew build` and `scripts/check-docs.sh` pass, a fresh clone compiles, the checklist of phases 6 to 9 is met, and a manual check with the compose database shows the report of patients registered and moved through triage.

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
- [ ] Validate on kind (kind 0.33.0 and kubectl 1.34.13 are in `~/.local/bin`)
- [ ] Azure: choose AKS or Container Apps (ADR 0010), set a budget alert, **an early smoke deploy**, record the URL
- [ ] Decide which LLM profile the deployed environment uses (ASSUMPTIONS A16)
- [ ] The assistant runs with `replicas: 1` (ASSUMPTIONS A39); triage and auth may run more
- [ ] Rate limit of the token endpoint behind the Ingress (from the review, finding A2): count only failed attempts, check the address before the username, set `server.forward-headers-strategy` explicitly and trust only the forwarded address of the Ingress (ASSUMPTIONS A28, ADR 0020)
- [ ] Database roles: one role per service instead of the shared superuser; the schema scripts run with an owner role (a Job or an init container) and the services with `spring.sql.init.mode: never`; the runtime role of triage only has `INSERT` and `SELECT` on `patient_audit` (ADR 0014)
- [ ] Compose: publish the infrastructure ports on `127.0.0.1` only and pin exact image tags (`pgvector/pgvector`, `redis`, also in the test containers); a password for Redis
- [ ] The Ingress does not route `/actuator` (ADR 0020, ASSUMPTIONS A29); the Redis of the deployment uses `maxmemory-policy noeviction`; the retention of `triaje.eventos` is set explicitly (7 days, ADR 0021)
- [ ] `auth.users` comes from a Kubernetes Secret (ARCHITECTURE section 10); auth logs a warning when it starts without users

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

- [ ] Source repository with the complete code (public, or private with access for the people who need it)
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

### Platform foundation (done, between phases 3 and 4)

- **What:** the cross-cutting gaps found after phase 3, built in one feature branch and one pull request.
  - New library `triaxis-web`: the single writer of error responses (RFC 7807 with `instance`, `traceId` and `timestamp`), the base class of the global exception handlers, the 401 and 403 handlers, the access log and the protected metrics endpoint. Triage and auth use it; the duplicated error code was removed.
  - 401 and 403 now use the standard format with `WWW-Authenticate`.
  - OpenTelemetry through Micrometer Tracing with Reactor context propagation; the trace id is the correlation id (logs, errors, outbox, Kafka headers `correlationId` and `traceparent`). The custom correlation filter was removed. Jaeger was added to the compose file.
  - ECS JSON logs (plain text in the `local` profile).
  - Metrics: common tag `application`; `/actuator/prometheus` with HTTP Basic from `METRICS_USER` and `METRICS_PASSWORD`, denied when no password is set.
  - Health: readiness of triage includes the database and not Kafka; graceful shutdown.
  - Hardening: `aud` claim issued and checked, 64 KB body limit, rate limit on the token endpoint (429 with `Retry-After`).
  - The assistant skeleton gets the same configuration.
  - Process: one pull request per feature or phase, merged with rebase and merge (`GIT_WORKFLOW.md`); `check-docs.sh` also checks that every module is documented.
- **Decisions:** ADR 0018 (shared web module and error format), ADR 0019 (observability), ADR 0020 (hardening); assumptions A26 to A30.
- **Verified:** `./gradlew build` passes on JDK 21 (new tests: error format and the 401, 403, 413, 415 and 500 cases, protected metrics with and without credentials, access log content without bodies or tokens, audience, rate limit, trace id in the outbox and in Kafka headers). Manual check with the compose infrastructure: JSON logs carry `traceId` and `spanId`, a request with a `traceparent` header keeps its trace id through the outbox into the Kafka headers, Jaeger lists both services, the metrics endpoint answers 401 without or with wrong credentials and 200 with the right ones, and the local profile prints plain text logs.
- **Found while testing:** a JUnit run once hung in a WebTestClient call that has no timeout; it did not reproduce in ten later runs. A default JUnit timeout of 120 seconds was added to the services so a hang fails with a stack trace instead of blocking.
- **Not verified yet:** traces reaching Azure, the Prometheus scrape in a cluster, and the consumer side of the `traceparent` header (assistant, phase 5).

### Phase 4: Escalation (done)

- **What:** the domain rules `Patient.slaExceededAt`, `escalationDueAt` and `escalate` (strict limit, only `REGISTERED` and never escalated, status and `status_changed_at` unchanged); `PatientEvent.escalated`; the port `PatientRepository.escalateOverdue(now, limit)` with one atomic `UPDATE ... RETURNING` over a CTE with `FOR UPDATE SKIP LOCKED`, ordered by `registered_at, id`, and the status and flag checked again on the locked rows; `EscalateOverduePatientsUseCase`, which writes the flag, one audit entry (`ESCALATED`, actor `system`) and one `paciente_escalado` outbox event per patient in one unit of work, then increments `triage.escalations` by level; the `EscalationScheduler` input adapter (`triage.escalation.enabled`, `interval` PT30S, `batch-size` 100; cycles never overlap, a full batch starts another one, a failed cycle is logged and retried on the next tick).
- **Decisions:** ADR 0006; assumptions A31 (escalation events carry no request trace) and A32 (both statuses of the event and audit entry are the current one). The escalation rows are read completely before the audit and outbox statements run on the same connection. MapStruct reads `Patient.escalate(Instant)` as a fluent setter, so the entity mapper ignores it explicitly.
- **Tests:** domain (strict limit, statuses, already escalated, the escalation keeps the status); use case with fake ports (audit, event, metric, second run, oldest first and batch size, rollback when the audit or the event fails); PostgreSQL (escalated once and a second run does nothing, `EN_CONSULTA`, `ALTA` and escalated never selected, strict limit, order and batch size, flag, audit and outbox rolled back together, two concurrent runs escalate each of ten patients once, 25 rounds of a concurrent `PATCH` keep the audit order, the counter); Kafka (the event arrives with the level, both statuses and `escalado = true`); scheduler (full batches chained, a failed cycle does not stop the loop); BlockHound over the escalation; ArchUnit. Integration tests share one database, so each escalation test back-dates its patients to its own epoch, earlier than every previous one, and uses a fixed clock.
- **Verified:** `./gradlew build` passes on JDK 21 (triage: 235 tests). Manual check with the compose PostgreSQL and Kafka, auth and triage running, an SLA of 1 minute for level 1 and an interval of 5 seconds: a critical patient registered through the API was escalated about 65 seconds later; the audit shows `REGISTERED` by `nurse` and `ESCALATED` by `system` with the same status before and after; the patient stays `REGISTRADO` with `status_changed_at` unchanged; both outbox rows were published; `kafka-console-consumer` shows `paciente_escalado` with `escalado: true`, the level and the patient id as key; a later `PATCH` to `EN_CONSULTA` works and `escalado` stays true.
- **Not verified yet:** several replicas escalating at the same time on a real cluster (covered by the concurrent test only), and the alert reaction to the event (phase 7).

### Phase 5, pull request 1: RAG (done)

- **What:**
  - **Corpus:** PROTO-001 to PROTO-003 as Markdown, word for word as in the specification, one section per chunk.
  - **Ingestion:** idempotent (content hash and model). Validated at startup, embedded in the background with back-off (A37).
  - **Persistence:** `protocol_chunks` in pgvector with an untyped vector, accessed with plain R2DBC (ADR 0002).
  - **Use case:** question policy (1000 characters), retrieval gate, numbered excerpts, citation validator, sources from metadata (ADR 0007).
  - **Providers:** `mock` (bag of words, extractive answer) and Gemini through the Spring AI starters (ADR 0008).
  - **Resilience:** `ResilientLlmAdapter` with timeouts, the circuit breaker `llm`, and a 503 with `Retry-After` (ADR 0004).
  - **Endpoint:** `POST /asistente/consulta` (`DOCTOR`), MapStruct mapper, shared error format, strict JSON.
  - **Metrics:** `assistant.answers` and `assistant.llm.latency`.
- **Decisions:** ADRs 0002, 0004, 0007 and 0008; assumptions A36 (thresholds `mock` 0.70, Gemini 0.33) and A37 (corpus at startup).
- **Found while building:**
  - **Spring AI:** the embedding connection auto-configuration of Spring AI ignores `spring.ai.model.embedding.text=none` and asks for credentials, so it is excluded unless the `gemini` profile is active.
  - **Jackson:** it turns a number into text by default, so the coercion of numbers and booleans to text is disabled.
  - **Ingestion:** embedding two sections at once dropped the second error when the provider failed, so sections are embedded one at a time.
  - **BlockHound:** installing it in the middle of the test run broke the threads of the `parallel` scheduler and made timeouts hang. It is now installed when the test JVM starts (a JUnit `LauncherSessionListener`), so every assistant test runs under it.
  - **The ignore rule:** `out/` also matched every source package named `out`, so the output ports and adapters of the auth service had never been committed and a fresh clone did not compile. It was fixed in its own pull request before this one (#28: the rule is now `/out/` and `/*/out/`).
- **Verified:**
  - **Build:** `./gradlew :triaxis-assistant-service:test` passes (92 tests).
  - **`mock` against compose:** the corpus is stored (7 sections), and a second start embeds nothing.
  - **`gemini` with an invalid key:** the service starts and the embedding is retried with back-off.
  - **`gemini` with a real key, through the endpoint:**
    - the five covered questions are answered from the right protocol and section, and diabetes gets the notice without a model call;
    - three hard questions get the notice from the model: a drug the protocol does not name, a prompt injection asking for a dose, and the dose of a contraindicated drug;
    - latency is about 1 second per answer, after turning off thinking (the first answer took 16.5 seconds with it).
- **Not verified yet:** the urgency view (pull request 2), and the assistant in containers (phase 8).

### Phase 5, pull request 2: urgency view (done)

- **What:**
  - **Listener:** asynchronous Kafka listener `UrgencyViewListener` in its own group `assistant-urgency-view`. It returns a `Mono` and acknowledges after Redis applied the event (ADR 0001), with at most 64 records in flight.
  - **Store:** Lua script with the rules of ADR 0021 in `RedisUrgencyViewStore` (patient hashes, counters, metadata, 30-day discharged marker).
  - **Rebuild:** from the beginning of the topic when partitions are assigned and the store is empty.
  - **Endpoint:** `GET /asistente/estado-urgencias` (`NURSE`, `DOCTOR`) with `porNivel`, `total` and `actualizadoEn` (A35).
  - **Readiness and metrics:** readiness with Redis; metric `assistant.urgency.events`.
- **Decisions:** assumption A38 (invalid records are skipped and counted; rebuild only when partitions are assigned).
- **Found while building:**
  - **The Lua script:** BlockHound reported that a Redis script built from a resource reads the file again on a Redis I/O thread, so the script is read once at startup.
  - **The rebuild:** a seek to the beginning while the asynchronous listener runs did not replay the records, and in the full suite it left the partition stopped. The rebuild happens only when partitions are assigned, and the test restarts the listener container.
- **Verified:**
  - **Build:** `./gradlew :triaxis-assistant-service:test` passes (112 tests).
  - **Redis store:** duplicates, a missing registration, a discharge before the registration, the discharge marker with its expiry, counts per level, the newest time and the reset.
  - **Listener:** duplicates and discharges, a patient seen only in consultation, invalid records skipped while later ones are applied, and a lost view rebuilt after a restart.
  - **Endpoint:** shape and roles.
  - **Manual check with the compose infrastructure, auth, triage and the assistant:**
    - starting with an empty Redis rebuilt the view from the topic (the patient of the phase 4 check, still in consultation, was counted);
    - three registrations and two discharges through triage were reflected in the view within seconds;
    - a doctor reads the view, and a call without a token gets 401.
- **Not verified yet:** several assistant replicas sharing the consumer group, and the services in containers (phase 8).

### Review fixes of 2026-11-19 (done, between phases 5 and 6)

- **What:** a full review of phases 0 to 5 with the `code-reviewer` agent (4 scopes in parallel; report in `docs/private/reviews/2026-11-19-full-review.md`, not versioned). It found no critical finding and 7 high ones. Fixed, one commit each:
  1. **A6:** the model output is never logged. The reply is parsed with our own `ObjectMapper`, because the Spring AI converter logged the whole text at ERROR.
  2. **UTC:** database sessions run in UTC through a connection customizer, and every test JVM runs in `America/Bogota`.
  3. **A1:** the JWKS download has timeouts and a refresh limit for unknown key ids.
  4. **A3:** R2DBC pool and session limits; timeouts per escalation batch, per relay cycle and on the similarity search; a database failure of the assistant is a 503.
  5. **A4:** the urgency view uses its own marker key (`urgency:initialized`), and when Redis does not answer at partition assignment the consumer waits there, consuming nothing, until it does.
  6. **A5:** negative counts never break the view, and the assistant runs as a single replica (A39).
  7. **Security:** deny by default with a route rule test in every service; `exp` is required; tests for `alg: none` and HS256 with the public key.
  8. **Small items:** `toString` without secrets, the retries of Spring AI off (`max-attempts: 1`), the JUnit timeout in the assistant, and key files ignored.
- **Decisions:** ADR 0022 (deny by default, bounded waits, UTC sessions); assumptions A39 (single replica of the assistant) and A40 (the SLA is fixed at registration); A3, A12, A13, A28, A29 and A38 updated.
- **Moved to phase 8:** A2 (the rate limit behind the Ingress), database roles with the DDL out of the startup, compose ports on `127.0.0.1` with exact tags, and the Ingress without `/actuator`. The rest of the review is in the improvements below.
- **Branch protection:** it was marked done in phase 0 but did not exist. It was applied to `main` and `develop` on 2026-11-20 and checked through the API.
- **Verified:**
  - `./gradlew build` passes for every module (453 tests) and `scripts/check-docs.sh` passes.
  - Each fix has a test that fails without it: the log capture, the session zone, the slow and unreachable JWKS, the stuck batch, the lost view, the negative count, a route without a rule and a token without `exp`.
- **Re-review of the branch:** the `code-reviewer` agent, run on the diff, found 3 high findings introduced by these fixes, all fixed in the same branch:
  - a JWKS refresh dropped the known keys before the download succeeded (now the last good key set is kept and downloads are shared);
  - the relay cycle timeout was shorter than a slow but working cycle (now a budget stops the cycle between events, and the timeout is only a safety net, validated);
  - restarting the listener left retries in flight (now the consumer waits in the assignment instead).
  
  The new settings are validated at startup. Medium and low findings of the re-review are in the improvements below.
- **Engineering rules:** the rules that would have prevented these findings are in `CLAUDE.md` and in the definition of done of phases 6 to 9 above.

---

## Improvements (out of scope for now)

Ideas that were consciously left out of the first delivery:

From the review of 2026-11-19 (report in `docs/private/reviews/`, not versioned), accepted as debt:

- Outbox: a dead letter (attempts and last error per row, a `failed` state for errors that cannot be retried) and a time budget per cycle, so one event never blocks the rest and a degraded Kafka does not hold a transaction for minutes.
- Urgency listener: retry only transient Redis errors, count the retries, and pause the container instead of blocking the consumer thread when the records in flight are exhausted.
- LLM: a bulkhead (`resilience4j-bulkhead` is already on the classpath), a rate limit per user on `POST /asistente/consulta`, and an HTTP timeout in the Gemini client itself.
- Prompt hardening: the question delimited in its own message, the numbered excerpts apart, and lines of the question that imitate an excerpt (`[n] ...`) neutralized; a test with a malicious question.
- Embedding identity: include the dimensions and the task type in the stored model name, so a change of either embeds the corpus again.
- Deterministic race tests: force both interleavings of a concurrent `PATCH` and the escalation (held transactions), and replace the remaining `Thread.sleep` polling with controllable schedulers.
- Tests for auth: the use case `IssueTokenService` with fakes, the address limit on a real endpoint, the type of the decoder errors; BlockHound explicitly over the outbox relay.
- A dependency scanner (OWASP Dependency-Check, Dependabot or Trivy); it is a new dependency and needs approval.
- From the re-review of the fixes:
  - transient database errors (`statement_timeout`, `lock_timeout`, pool exhausted) answer 500 in triage instead of 503;
  - `assistant.rag.search-timeout` (3 s) is shorter than `statement_timeout` (10 s), so a cancelled search keeps its connection busy until the server stops it;
  - no test holds a relay cycle to prove its rollback;
  - the Gemini adapter is tested with a plain `ObjectMapper` instead of the one of Spring Boot.
- Smaller items: a cooperative `stop()` of the background loops (no duplicate sends on shutdown); `concatMap(..., 0)` after `onBackpressureDrop`; `send-timeout` greater than `max.block.ms` plus `delivery.timeout.ms`; instants truncated to microseconds in the clock; `TriageProperties` validated; BCrypt explicitly on `boundedElastic`; a bounded rate limiter map; RSA keys of at least 2048 bits checked at startup; unused dependencies (`spring-boot-starter-validation` in triage and the assistant, `spring-boot-starter-aop` in the assistant, `triaxis-common` and the resource server starter in auth); leftovers of the project generator in `triaxis-triage-service/.gitignore` and `.gitattributes`; a JaCoCo threshold and the foojay toolchain resolver; a metric of failed logins; the Lua script of the urgency view uses keys of different slots (not compatible with Redis Cluster); records of the event contract with primitives default to 0 or false when a field is missing.

- Read endpoint for the clinical audit (`GET /triage/pacientes/{id}/auditoria`); the audit is written and protected, but only readable through the database.
- Write the trace id as the nested ECS field `trace.id` instead of the top-level `traceId` of the JSON log line.
- A distributed rate limiter and account lockout for the token endpoint.
- Key rotation procedure for the JWT signing key and token revocation.
