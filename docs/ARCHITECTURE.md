# Triaxis: Architecture

Architecture and design decisions for **Triaxis**, a digital triage system with a clinical assistant.
Companion documents: [`TECH_STACK.md`](TECH_STACK.md) (stack and versions) and [`adr/`](adr/) (decision records).

Status: **living document**. It describes the system as designed and built; when a design decision changes it is updated in the same pull request that changes the code, and the decision is recorded in an ADR (see section 14 for their status). `docs/PLAN.md` tracks the progress.

---

## 1. Scope and goals

Triaxis replaces a manual triage process (a nurse writes a priority level on paper) with:

1. **Triage service**: registers patients with vital signs, classifies them automatically, controls their clinical state, watches waiting-time SLAs, escalates overdue patients automatically, and keeps a complete audit trail.
2. **Clinical Assistant service**: lets doctors query internal clinical protocols in natural language (RAG). It must never invent clinical content: every answer is backed by the official corpus and cites its source.
3. **Auth service**: issues JWTs so the other two services can authorize requests without sharing secrets.

Non-functional goals: non-blocking reactive code (no blocking calls on the event loop), asynchronous integration through Kafka, resilience when the LLM provider fails, observability, and one-command local execution.

Backend only. No frontend.

---

## 2. Requirements traceability

| Requirement | Where it is handled |
|---|---|
| RF-01 Register and classify patients | `triage-service`: `RegisterPatientUseCase`, `TriageClassifier` (pure domain logic) |
| RF-02 Clinical state flow | `triage-service`: `PatientStatus` transition policy, conditional `UPDATE` |
| RF-03 SLA monitoring and escalation | `triage-service`: `EscalationScheduler` + atomic `UPDATE ... RETURNING` |
| RF-04 A Clinical assistant (RAG) | `assistant-service`: `AskClinicalQuestionUseCase` |
| RF-04 B Real-time urgency state | `assistant-service`: Kafka consumer + Redis read model |
| RF-05 Operation report | `triage-service`: advanced SQL (window functions, `FILTER`, percentile) |
| RF-06 Escalation alerts (open) | `triage-service`: Kafka-driven `AlertNotifier` (see 6.8 and ADR) |
| RN-01 Classification by level | `TriageClassifier` |
| RN-02 SLA per level | `SlaPolicy` (configurable) |
| RN-03 Escalation idempotency | Conditional `UPDATE`, `FOR UPDATE SKIP LOCKED` |
| RN-04 Clinical audit | `patient_audit` table with the actor, append-only, no delete endpoint (ADR 0014) |
| RN-05 Anti-hallucination | Retrieval threshold + validated citations (see 7.2) |
| Resilience4j on the LLM | `assistant-service`: circuit breaker, timeout, 503 fallback |
| Observability | Actuator probes, Micrometer metrics (protected Prometheus endpoint), ECS JSON logs, OpenTelemetry traces (ADR 0019) |
| Security | JWT (RS256) from `auth-service`, resource server in the other two |

---

## 3. System overview

```mermaid
flowchart LR
    client([Client / evaluator])

    subgraph Triaxis
        auth[auth-service<br/>JWT RS256 + JWKS]
        triage[triage-service]
        assistant[assistant-service]
    end

    pgT[(PostgreSQL<br/>triage db)]
    pgA[(PostgreSQL + pgvector<br/>assistant db)]
    kafka{{Kafka<br/>triaje.eventos}}
    redis[(Redis<br/>urgency view)]
    llm[[Gemini API<br/>chat + embeddings]]
    hook[[Webhook<br/>alert channel]]

    client -->|POST /auth/token| auth
    client -->|JWT| triage
    client -->|JWT| assistant

    triage --> pgT
    triage -->|outbox relay| kafka
    kafka -->|paciente_escalado| triage
    triage -->|alerts| hook

    kafka -->|all events| assistant
    assistant --> redis
    assistant --> pgA
    assistant -->|circuit breaker| llm

    triage -. JWKS .-> auth
    assistant -. JWKS .-> auth
```

Key properties:

- **Triage never depends on the assistant.** The only link between them is the Kafka topic, and it goes from Triage to Assistant.
- **The urgency view does not depend on the LLM.** If Gemini is down, `GET /asistente/estado-urgencias` still works.
- **Each service owns its database** (separate databases in the same PostgreSQL server).

---

## 4. Services

| Service | Port | Datastores | Base path |
|---|---|---|---|
| `triaxis-triage-service` | 8081 | PostgreSQL (`triage`), Kafka producer and consumer (alerts) | `/triage/**` |
| `triaxis-assistant-service` | 8082 | PostgreSQL + pgvector (`assistant`), Redis, Kafka consumer | `/asistente/**` |
| `triaxis-auth-service` | 8083 | None (in-memory users from configuration) | `/auth/**`, `/.well-known/jwks.json` |
| `triaxis-common` | n/a | Library: event contract records | n/a |
| `triaxis-security` | n/a | Library: shared resource-server configuration | n/a |
| `triaxis-web` | n/a | Library: standard error responses, access log, protected metrics, tracing dependencies (ADR 0018) | n/a |

Path-based routing works behind a single Ingress without rewrites, because each service already owns a distinct base path.

---

## 5. Architectural style

**Hexagonal architecture (ports and adapters)** in each service. The domain does not know Spring, R2DBC, Kafka or the LLM provider.

```
dev.triaxis.<service>
├── domain
│   ├── model            entities, value objects, enums
│   ├── service          pure domain logic (classifier, policies)
│   └── exception
├── application
│   ├── port.in          use cases (interfaces)
│   ├── port.out         repositories, publishers, LLM, clock (interfaces)
│   └── service          use case implementations
├── infrastructure
│   ├── adapter.in       web (functional endpoints, see 5.1), scheduler, Kafka listeners
│   ├── adapter.out      persistence (R2DBC), messaging, LLM, Redis
│   └── config           Spring configuration, properties
└── <Service>Application
```

Rules:

1. `domain` depends on nothing but the JDK.
2. `application` depends on `domain` only.
3. `infrastructure` depends on both; nothing depends on `infrastructure`.
4. The external contract (Spanish names, JSON shape) is mapped at the boundary (DTOs and mappers). The domain uses English names.
5. Boundaries are enforced with **ArchUnit** tests.
6. Time always comes from a `Clock` port (UTC), so SLA logic is testable without sleeping.

### 5.1 Web layer: functional endpoints

The web adapters use **WebFlux functional endpoints** (`RouterFunction` and `HandlerFunction`) instead of `@RestController`. A router declares the routes, a handler contains one method per route, and both are plain adapters that call a use case.

Detailed layout of `triage-service` (the other services follow the same shape):

```
dev.triaxis.triage
├── domain
│   ├── model            Patient, VitalSigns, TriageLevel, PatientStatus, AuditEntry, DomainEvent
│   ├── rule             TriageLevelRule (interface) + one class per level   (Strategy)
│   ├── service          TriageClassifier, SlaPolicy, StatusTransitionPolicy
│   └── exception
├── application
│   ├── port.in          RegisterPatientUseCase, ChangePatientStatusUseCase, GetPatientUseCase,
│   │                    EscalateOverduePatientsUseCase, GetOperationReportUseCase
│   ├── port.out         PatientRepository, AuditRepository, EventPublisher, ClockPort,
│   │                    IdGenerator, UnitOfWork, TriageMetrics  (later: ReportQuery, AlertNotifier)
│   └── service          use case implementations
├── infrastructure
│   ├── adapter.in
│   │   ├── web
│   │   │   ├── router       PatientRouter, ReportRouter           (RouterFunction beans)
│   │   │   ├── handler      PatientHandler, ReportHandler
│   │   │   ├── dto          request and response records (Spanish external names)
│   │   │   ├── mapper       MapStruct: DTO <-> command and domain <-> response
│   │   │   ├── validation   InvalidRequestException (JSON shape only; business validation is in the domain, ADR 0017)
│   │   │   └── error        GlobalErrorHandler (WebExceptionHandler)
│   │   ├── scheduler        EscalationScheduler
│   │   └── messaging        EscalationAlertListener
│   ├── adapter.out
│   │   ├── persistence      entity, R2DBC repositories, repository adapters, report query
│   │   ├── messaging        OutboxEventPublisher, OutboxRelay
│   │   └── alert            WebhookAlertNotifier, LoggingAlertNotifier
│   └── config               Spring configuration, properties
└── TriageApplication
```

Consequences of using functional endpoints (and how each is handled):

| Topic | With `@RestController` | With functional endpoints (this project) |
|---|---|---|
| Bean validation | `@Valid` on the body | Handler calls `RequestValidator` (Spring `Validator`), which collects violations and throws a validation exception naming the field |
| Error handling | `@RestControllerAdvice` | `@RestControllerAdvice` does **not** apply; a `WebExceptionHandler` maps exceptions to `ProblemDetail` |
| Authorization | Often `@PreAuthorize` | Path and method rules in `SecurityWebFilterChain` (for example `POST /triage/pacientes` requires `NURSE`) |
| OpenAPI | Automatic | Not automatic; keep a hand-written `openapi.yaml` or add springdoc router annotations (optional) |
| Controller tests | `@WebFluxTest` | `WebTestClient.bindToRouterFunction(router)` |

### 5.2 SOLID and design patterns

Where each principle shows up:

| Principle | Where |
|---|---|
| **S**ingle responsibility | Handler translates HTTP, use case orchestrates, domain decides, adapter does I/O. Each class has one reason to change |
| **O**pen/closed | A new classification rule, alert channel or LLM provider is a new class; existing ones are not modified |
| **L**iskov substitution | `LlmPort` (Gemini or mock) and `AlertNotifier` (webhook or log) implementations are interchangeable without the caller noticing |
| **I**nterface segregation | Small ports: `PatientRepository`, `AuditRepository`, `ReportQuery` and `EventPublisher` are separate instead of one large repository |
| **D**ependency inversion | `application` depends on `port.out` interfaces; `infrastructure` implements them; Spring wiring lives in `config` |

Patterns and their location (used only where they solve a real problem):

| Pattern | Where | Purpose |
|---|---|---|
| Ports and adapters | `application/port`, `infrastructure/adapter` | Isolate the domain from frameworks |
| Strategy (ordered list of rules) | `domain/rule` | One `TriageLevelRule` per level; the classifier evaluates them from most to least severe |
| Enum-based state machine | `domain/model/PatientStatus` | Allowed transitions live next to the states |
| Static factory | `Patient.register(...)`, event factory | Create entities in a valid state |
| Repository | `port.out` plus R2DBC adapters | Persistence abstraction |
| Decorator | `ResilientLlmAdapter` wraps the provider adapter (Gemini or mock) with circuit breaker and timeouts (ADR 0004) | Resilience without polluting the adapter, explicit and testable instead of annotations |
| Anti-corruption layer / Mapper | `adapter.in.web.mapper`, persistence mappers | External contract (Spanish) and storage never leak into the domain |
| Transactional outbox | `adapter.out.messaging` | Reliable event publication |
| Null object | `LoggingAlertNotifier` as the default notifier | The system works without an external webhook |
| Builder | Response and prompt construction | Readable construction of complex objects |
| Domain events | Use cases emit events through `EventPublisher` | Decouple state changes from their consequences |

Guideline: no pattern is added only to name it. If a class would have a single implementation and no seam is needed, keep it simple.

Reactive rules:

- No blocking call on an event-loop thread. Blocking SDK calls (`EmbeddingModel`, blocking client code) run on `Schedulers.boundedElastic()`.
- Optional but valuable: BlockHound in tests to prove nothing blocks.
- Bounded concurrency everywhere (`flatMap(..., concurrency)`), explicit timeouts on all remote calls.

---

## 6. Triage service

### 6.1 Domain

- `Patient` (id, full name, age, `VitalSigns`, chief complaint, level, SLA minutes, status, escalated flag, timestamps).
- `VitalSigns`: heart rate, systolic, diastolic, SpO2, temperature, pain, GCS.
- `TriageLevel`: `CRITICAL`, `URGENT`, `URGENT_2`, `NOT_URGENT`, `NOT_URGENT_2`, with no external value in the domain; the mappers convert them to `CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2` (ADR 0015).
- `PatientStatus`: `REGISTERED`, `IN_CONSULTATION`, `DISCHARGED` (external `REGISTRADO`, `EN_CONSULTA`, `ALTA`).

### 6.2 Classification (RN-01)

`TriageClassifier` is a pure function: vital signs plus complaint in, level out. Conditions are evaluated from the most severe level downward, and the first match wins.

| Level | Condition (any) |
|---|---|
| 1 CRITICAL | HR < 40 or HR > 120, SpO2 < 90, systolic < 90, GCS <= 8 |
| 2 URGENT | 90 <= SpO2 <= 93, 90 <= systolic <= 100, temp >= 38.5, 9 <= GCS <= 13 |
| 3 URGENT_2 | 90 <= HR <= 120, 38.0 <= temp < 38.5, pain >= 7 |
| 4 NOT_URGENT | none of the above |
| 5 NOT_URGENT_2 | level 4 **and** complaint is administrative |

Assumptions (also recorded in the README):

- **A1.** HR, systolic, diastolic, SpO2, pain, GCS and age are integers. Decimals are rejected with 400. This removes the gap between 93 and 94 for SpO2. Temperature has one decimal.
- **A2.** GCS 14 and 15 do not trigger any level by themselves.
- **A3.** The administrative complaint list (`documentos`, `recoleccion de resultados`, `renovacion de recetario`, `vacunacion`, `seguimiento`) is configurable. Matching uses **normalized exact equality** (lowercase, accents removed, trimmed), not "contains", so a clinical complaint that merely mentions "seguimiento" is not downgraded.
- **A4.** Validation ranges follow the specification table (HR 20-250, systolic 60-260, diastolic 30-160 and lower than systolic, SpO2 70-100, temp 34.0-42.0, pain 0-10, GCS 3-15, age 0-120, name 3-100 characters). The error message names the invalid field.

Reference cases E1 to E6 become unit tests.

### 6.3 State machine (RF-02)

```
REGISTERED ──► IN_CONSULTATION ──► DISCHARGED
     └───────────────────────────────►┘
```

Any other transition (including same state) returns **409** with a descriptive message. Unknown patient returns **404**. An escalated patient can still move to `IN_CONSULTATION`; the flag stays.

The change is a conditional update, so concurrent requests cannot both succeed:

```sql
UPDATE patients
SET status = :new,
    status_changed_at = :now,
    consultation_started_at = CASE WHEN :new = 'IN_CONSULTATION' THEN :now ELSE consultation_started_at END
WHERE id = :id AND status = :expected
RETURNING *;
```

Zero rows returned means: re-read the patient, then 404 (missing) or 409 (state changed or invalid).

### 6.4 Persistence

```sql
CREATE TABLE patients (
  id                      UUID PRIMARY KEY,
  full_name               VARCHAR(100) NOT NULL,
  age                     SMALLINT NOT NULL,
  heart_rate              SMALLINT NOT NULL,
  systolic                SMALLINT NOT NULL,
  diastolic               SMALLINT NOT NULL,
  spo2                    SMALLINT NOT NULL,
  temperature             NUMERIC(3,1) NOT NULL,
  pain                    SMALLINT NOT NULL,
  gcs                     SMALLINT NOT NULL,
  chief_complaint         TEXT NOT NULL,
  level                   VARCHAR(16) NOT NULL,
  sla_minutes             INTEGER NOT NULL,
  status                  VARCHAR(16) NOT NULL,
  escalated               BOOLEAN NOT NULL DEFAULT FALSE,
  escalated_at            TIMESTAMPTZ,
  registered_at           TIMESTAMPTZ NOT NULL,
  status_changed_at       TIMESTAMPTZ NOT NULL,
  consultation_started_at TIMESTAMPTZ
);
CREATE INDEX idx_patients_sla ON patients (status, escalated, registered_at);

CREATE TABLE patient_audit (
  id              BIGSERIAL PRIMARY KEY,
  patient_id      UUID NOT NULL,
  actor           VARCHAR(100) NOT NULL,   -- JWT subject, or 'system' for the scheduler
  event_type      VARCHAR(32) NOT NULL,
  previous_status VARCHAR(16),
  new_status      VARCHAR(16) NOT NULL,
  escalated       BOOLEAN NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL
);
-- Append-only: a trigger rejects UPDATE and DELETE; the API exposes no delete endpoint.

CREATE TABLE outbox_events (
  id           BIGSERIAL PRIMARY KEY,
  event_id     UUID NOT NULL UNIQUE,
  message_key  VARCHAR(64) NOT NULL,
  event_type   VARCHAR(32) NOT NULL,
  correlation_id VARCHAR(64),             -- trace id of the request, sent as the Kafka header correlationId
  traceparent    VARCHAR(64),             -- W3C trace context, sent as the Kafka header traceparent
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (id) WHERE published_at IS NULL;
```

The schema is created by initialization scripts executed through R2DBC (no Flyway, to avoid a second JDBC driver). The R2DBC connection uses UTC as session time zone.

### 6.5 Events and the transactional outbox

Every state change writes **patient row, audit entry and outbox row in one transaction** (`TransactionalOperator`). A relay then publishes outbox rows to Kafka:

- Polls `WHERE published_at IS NULL ORDER BY id LIMIT n FOR UPDATE SKIP LOCKED`, sends, then marks as published.
- Single relay in flight (advisory lock) to preserve per-patient ordering.
- Producer: `acks=all`, idempotence enabled, key = patient id (ordering per patient within a partition).
- Delivery is **at least once**; consumers are idempotent.

Why: publishing to Kafka directly after the database commit can lose the event (or the other way around). The outbox removes that dual-write problem.

Implemented in full (ADR 0005), including the trace columns and the metrics `triage.outbox.pending`, `triage.outbox.published` and `triage.outbox.failures`; the fallback of publishing after the commit was not needed. The relay loop is configurable and is switched off in tests, which call it explicitly.

### 6.6 Escalation (RF-03, RN-03)

A scheduler runs every `triage.escalation.interval` (configurable) and escalates in **one atomic statement**:

```sql
WITH overdue AS (
  SELECT id FROM patients
  WHERE status = 'REGISTERED'
    AND NOT escalated
    AND registered_at + make_interval(mins => sla_minutes) < :now
  ORDER BY registered_at, id
  LIMIT :batch
  FOR UPDATE SKIP LOCKED
)
UPDATE patients p
SET escalated = TRUE, escalated_at = :now
FROM overdue o
WHERE p.id = o.id AND p.status = 'REGISTERED' AND NOT p.escalated
RETURNING p.*;
```

For each returned row (oldest first), in the same transaction: insert the audit entry (type `ESCALATED`, actor `system`, the same status before and after) and the outbox row (`paciente_escalado`, with `estadoAnterior` and `estadoNuevo` both `REGISTRADO` and `escalado: true`). The status and `status_changed_at` do not change. The counter `triage.escalations` (tag `level`) is incremented after the commit. Implemented by `EscalateOverduePatientsUseCase`, `PatientRepository.escalateOverdue(now, limit)` and the `EscalationScheduler` input adapter (ADR 0006).

Why this satisfies RN-03:

- The `WHERE` makes a second attempt a no-op (already escalated, or no longer `REGISTERED`), so there is no duplicate event or audit row.
- A patient who entered consultation or was discharged is never selected.
- `SKIP LOCKED` makes it safe with several replicas.
- A concurrent `PATCH` to `IN_CONSULTATION` and the scheduler are ordered by the row lock: if the `PATCH` commits first, the patient is no longer `REGISTERED` and is never escalated; if the escalation commits first, the `PATCH` then proceeds, because an escalated patient may still enter consultation. An escalation entry therefore never follows a consultation or discharge entry in the audit.

```mermaid
sequenceDiagram
    participant S as EscalationScheduler
    participant DB as PostgreSQL
    participant R as Outbox relay
    participant K as Kafka
    participant N as AlertNotifier

    S->>DB: UPDATE ... RETURNING (atomic, conditional)
    DB-->>S: escalated patients
    S->>DB: INSERT audit + outbox (same transaction)
    R->>DB: poll pending outbox rows
    R->>K: publish paciente_escalado
    R->>DB: mark published
    K-->>N: paciente_escalado
    N->>N: send alert (webhook), retry with backoff
```

The interval (`triage.escalation.interval`, 30 seconds by default), the batch size (`triage.escalation.batch-size`, 100) and the SLA per level are application properties, so a manual check can use a short SLA. Cycles never overlap, a full batch starts another one at once, and a failed cycle is logged and retried on the next tick. The loop is switched off in tests (`triage.escalation.enabled=false`), which call the use case with a fixed clock and back-dated patients.

### 6.7 Operation report (RF-05)

```sql
WITH hourly AS (
  SELECT date_trunc('hour', registered_at) AS hour,
         count(*)                                         AS total,
         count(*) FILTER (WHERE level = 'CRITICAL')       AS critical,
         count(*) FILTER (WHERE level = 'URGENT')         AS urgent,
         count(*) FILTER (WHERE level = 'URGENT_2')       AS urgent_2,
         count(*) FILTER (WHERE level = 'NOT_URGENT')     AS not_urgent,
         count(*) FILTER (WHERE level = 'NOT_URGENT_2')   AS not_urgent_2,
         avg(extract(epoch FROM consultation_started_at - registered_at) / 60)
             FILTER (WHERE consultation_started_at IS NOT NULL)            AS avg_wait_min,
         percentile_cont(0.95) WITHIN GROUP
             (ORDER BY extract(epoch FROM consultation_started_at - registered_at) / 60)
             FILTER (WHERE consultation_started_at IS NOT NULL)            AS p95_wait_min
  FROM patients
  WHERE registered_at >= :from AND registered_at < :to
  GROUP BY 1
)
SELECT h.*,
       sum(total) OVER (ORDER BY hour)      AS cumulative_total,
       rank()     OVER (ORDER BY total DESC) AS load_rank
FROM hourly h
ORDER BY hour;
```

Assumptions: waiting time is registration to the first (and only) transition into consultation; patients discharged directly have no waiting time and are excluded from the average and p95; hours without patients are omitted.

### 6.8 Alerts to the medical team (RF-06, open requirement)

**Proposal:** a Kafka-driven `AlertNotifier` port inside `triage-service`.

- A separate consumer group subscribes to `triaje.eventos` and reacts to `paciente_escalado`.
- Adapter `WebhookAlertNotifier` posts a small JSON message (compatible with Slack or Teams incoming webhooks) to a configurable URL. Default adapter: structured log, so the system works without any external account.
- Retries with backoff; a failure to notify is counted (metric) and never affects the escalation itself.

Why: alerting is a reaction to a domain event, so it is decoupled from the escalation transaction; it adds no fourth service; and it is testable with a fake webhook.

Alternatives considered (to document in the ADR): email through SMTP (needs infrastructure), Server-Sent Events stream (good demo, but only works within one replica unless fed from Kafka), a dedicated `notification-service` (cleanest separation, but more deployment cost).

### 6.9 API contract

External names are Spanish, as specified.

| Method and path | Role | Success | Errors |
|---|---|---|---|
| `POST /triage/pacientes` | NURSE | 201 with `id`, `nivel`, `slaMinutos`, `estado: "REGISTRADO"`, `fechaRegistro` (ISO 8601) | 400 (invalid field), 401, 403 |
| `PATCH /triage/pacientes/{id}/estado` body `{ "estado": "..." }` | NURSE, DOCTOR | 200 | 400, 404, 409 |
| `GET /triage/pacientes/{id}` | NURSE, DOCTOR | 200: full data, `estado`, flags (`escalado`), time in current state | 404 |
| `GET /triage/reportes/operacion?desde=&hasta=` | DOCTOR | 200: list of hourly buckets | 400 (invalid range) |

Request body fields: `nombreCompleto`, `edad`, `frecuenciaCardiaca`, `presionSistolica`, `presionDiastolica`, `spo2`, `temperatura`, `dolor`, `gcs`, `quejaPrincipal`. Because the exact field names are not given, common variants are accepted with `@JsonAlias` (for example `nombre`, `fc`, `heartRate`), and the choice is documented in the README.

`POST` answers 201 with a `Location` header. `PATCH` answers 200 with the same body as `GET`. `GET` returns the registration data, `nivel`, `slaMinutos`, `estado`, `escalado`, `fechaRegistro`, `fechaCambioEstado` and `tiempoEnEstadoSegundos`.

All errors use RFC 7807 `ProblemDetail`. Validation errors (400) add `errores`, a list of `campo` (the external field name) and `mensaje`. Transition errors (409) use the external status names. Integers are parsed strictly (a decimal or a string is a 400 naming the field). See ADR 0012, ADR 0017 and ASSUMPTIONS A25.

---

## 7. Clinical Assistant service

### 7.1 RAG pipeline

```mermaid
flowchart TD
    q[POST /asistente/consulta] --> v[Validate question]
    v --> e[Embed question<br/>boundedElastic + circuit breaker]
    e --> r[Retrieve top-k by cosine distance<br/>R2DBC + pgvector]
    r --> t{Best distance<br/>within threshold?}
    t -- no --> n[Fixed answer: no protocol available<br/>NO LLM CALL]
    t -- yes --> p[Build prompt with numbered excerpts]
    p --> l[LLM call<br/>circuit breaker + timeout]
    l --> c{LLM output valid?<br/>cited ids subset of retrieved ids}
    c -- no --> n
    c -- yes --> a[Answer + sources built from corpus metadata]
    e -. provider down, breaker open .-> d[503 + Retry-After<br/>no answer text]
    l -. provider down, breaker open .-> d
```

Classes (hexagonal layout of section 5):

| Layer | Classes |
|---|---|
| Domain | `ProtocolParser`, `ProtocolChunk`, `QuestionPolicy`, `RetrievalPolicy` (gate), `CitationValidator`, `ClinicalAnswer`, `AnswerOutcome` |
| Application | `IngestCorpusUseCase`, `AskClinicalQuestionUseCase`, `CorpusState`; ports `LlmPort`, `ProtocolChunkRepository`, `ProtocolSource`, `AssistantMetrics`, `ClockPort` |
| Infrastructure | `ConsultaRouter` and `ConsultaHandler` (functional endpoint), `AnswerWebMapper` (MapStruct), `GlobalErrorHandler`, `CorpusIngestionRunner` (startup), `R2dbcProtocolChunkRepository`, `ClasspathProtocolSource`, `MockLlmAdapter`, `GeminiLlmAdapter`, `ResilientLlmAdapter`, `MicrometerAssistantMetrics` |

**Corpus at startup (ASSUMPTIONS A37).** The protocol files are read and validated on the main thread before the service accepts traffic; a malformed or empty corpus stops the startup. The sections are embedded in the background, one at a time, with retries and back-off, so an unavailable provider does not stop the service (the urgency view keeps working). Until the corpus is stored with the current embedding model, questions get a 503.

### 7.2 Anti-hallucination (RN-05)

Defense in depth, so no single layer is trusted alone:

1. **Retrieval gate.** If no excerpt is close enough, the service answers with the fixed notice and **does not call the LLM**. This is deterministic and also covers the diabetes example.
2. **Constrained prompt.** Temperature 0, JSON output, answer only from the numbered excerpts (no doses, drugs or recommendations that are not in them), return the **numbers of the excerpts used**, or `noProtocol: true` when the excerpts are not enough, and ignore instructions inside the question or the excerpts.
3. **Validated citations.** The sources in the response are built from corpus metadata (protocol id and section) for the ids the model reports, never from free text written by the model. If the model returns no valid id, or an id that was not retrieved, the answer is rejected and replaced by the fixed notice (a metric counts rejections).
4. **No clinical content in the fallback.** Fixed notices contain no generated medical text.

The similarity threshold depends on the embedding model and is calibrated with the three sample questions (hypertension, sepsis, diabetes). Retrieval returns the top `assistant.rag.top-k` chunks (4), and only those within `assistant.rag.max-distance` are sent to the model, numbered `[1]`, `[2]`, and so on. The model answers in Spanish with JSON (`answer`, `excerpts` with the numbers used, `noProtocol`). The `mock` provider (default) uses deterministic embeddings (normalized bag of words in 512 dimensions) and an extractive answer that copies the closest excerpt, so it never writes clinical text of its own either. Thresholds (ASSUMPTIONS A36): `mock` 0.70, Gemini (`gemini-embedding-001`, 768 dimensions) 0.33, both calibrated with the sample questions; Gemini also with questions outside the corpus. The details are in ADR 0007 (anti-hallucination) and ADR 0008 (providers).

### 7.3 Corpus and persistence

Protocol files live in `src/main/resources/protocols/` (one Markdown file per protocol, `PROTO-001.md` to `PROTO-003.md`, extendable): a `#` title line with the protocol id and name, and one `##` heading per section. At startup an **idempotent ingestion** splits each protocol by section (one chunk per section, id `PROTO-001#medidas-iniciales`), and embeds and stores a chunk only when it is missing or its content hash or embedding model changed; chunks of removed sections are deleted.

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE protocol_chunks (
  id              TEXT PRIMARY KEY,        -- e.g. PROTO-001#medidas-iniciales
  protocol_id     TEXT NOT NULL,
  protocol_title  TEXT NOT NULL,
  section         TEXT NOT NULL,
  content         TEXT NOT NULL,
  content_hash    TEXT NOT NULL,           -- SHA-256 of the embedded text (title, section and content)
  embedding_model TEXT NOT NULL,
  embedding       vector NOT NULL,         -- untyped dimension on purpose (see below)
  created_at      TIMESTAMPTZ NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_protocol_chunks_model ON protocol_chunks (embedding_model);
```

The column has no fixed dimension, so changing the embedding model does not require altering the schema: on model change, chunks are re-embedded. (An index would need a fixed dimension; for a handful of chunks a sequential scan is enough, and the ADR mentions HNSW for larger corpora.)

Retrieval query:

```sql
SELECT id, protocol_id, protocol_title, section, content, content_hash,
       embedding <=> CAST(:query AS vector) AS distance
FROM protocol_chunks
WHERE embedding_model = :model
ORDER BY distance, id
LIMIT :k;
```

Vectors are sent as text with a `CAST(... AS vector)` through R2DBC `DatabaseClient`, so the whole retrieval path is reactive (ADR 0002). Spring AI is still used for `EmbeddingModel`, `ChatClient` and the structured output converter.

### 7.4 Degraded mode

Resilience4j circuit breaker `llm` wraps both the embedding and the chat call, with a time limiter. Failure rate threshold, sliding window and wait duration are properties. HTTP 429 and 5xx count as failures. When the breaker is open (or the call fails), the service answers **503** with a clear message. Triage is never affected.

### 7.5 Urgency view from Kafka (RF-04 B)

The assistant **never calls the triage API**. A consumer in its own group (`assistant-urgency-view`) maintains a Redis read model:

- `urgency:patient:{id}` (hash: level, state `active` or `discharged`), `urgency:counters` (hash: level to count) and `urgency:meta` (time of the newest applied event).
- A **Lua script** applies each event atomically and idempotently (ADR 0021):

| Event | Patient unknown | Patient active | Patient discharged |
|---|---|---|---|
| `paciente_registrado`, `paciente_en_consulta`, `paciente_escalado` | store as active with the event level, add 1 | no change | no change |
| `paciente_alta` | store as discharged, no count change | mark discharged, subtract 1 | no change |

- Discharged is final; its key expires after `assistant.urgency.discharged-ttl` (30 days, longer than the topic retention), so a replay never counts a discharged patient again.
- The listener (`UrgencyViewListener`) is asynchronous, as ADR 0001 says: it returns a `Mono` and acknowledges a record only after the script succeeded, so offsets are committed only for applied events. Records may be applied out of order, which the rules allow. At most `assistant.urgency.max-in-flight` (64) records are in flight; a Redis failure is retried with back-off until it works; a record that is not a valid event is logged (without its payload), counted and skipped (ASSUMPTIONS A38).
- If Redis is lost (no `urgency:meta` key), the consumer reads the topic again from the earliest offset when its partitions are assigned, at startup or after a restart, before it processes any record, and the same rules rebuild the counts. The view is not rebuilt while the consumer runs: with asynchronous acknowledgments a seek does not replay the records.
- The Lua script is read once at startup (`redis/urgency-apply.lua`); a script built from a resource reads the file again on a Redis I/O thread, which BlockHound reported.
- Metric `assistant.urgency.events` by outcome (`applied`, `unchanged`, `invalid`). The listener continues the trace of the `traceparent` header (Kafka observation); `correlationId` is optional (A31).

`GET /asistente/estado-urgencias` returns all five levels (zeros included), a total and the time of the newest applied event:

```json
{
  "porNivel": { "CRITICO": 1, "URGENTE": 2, "URGENTE_2": 0, "NO_URGENTE": 3, "NO_URGENTE_2": 1 },
  "total": 7,
  "actualizadoEn": "2026-11-20T15:30:00Z"
}
```

`actualizadoEn` is null while no event was applied.

### 7.6 API contract

| Method and path | Role | Success | Errors |
|---|---|---|---|
| `POST /asistente/consulta` body `{ "pregunta": "..." }` | DOCTOR | 200 `{ "respuesta": "...", "fuentes": [ { "protocolo": "PROTO-001", "seccion": "..." } ] }`. If no protocol applies: 200 with `respuesta` = "No existe un protocolo disponible para esta pregunta." and `fuentes: []` | 400 (blank question or longer than 1000 characters), 401, 403, 503 (LLM unavailable) |
| `GET /asistente/estado-urgencias` | NURSE, DOCTOR | 200 `{ "porNivel": {...five levels...}, "total": n, "actualizadoEn": "..." }` (section 7.5) | 401, 403 |

`seccion` is the section title as written in the corpus (for example `Medidas iniciales`). Errors use the shared RFC 7807 format (section 10).

---

## 8. Authentication and authorization

- `auth-service` exposes `POST /auth/token` (credentials from configuration, passwords stored hashed) and `GET /.well-known/jwks.json`.
- Tokens are **RS256**, short-lived (15 minutes), with `sub`, `roles`, `iss`, `aud`, `iat`, `exp`, `jti` and a `kid` header. The resource servers check signature, issuer, audience and expiration (ADR 0003 and 0020).
- The private key comes from an environment variable or secret. Without it the service refuses to start, unless `auth.jwt.generate-key-if-missing` is on (local profile only), which generates a key pair with a warning.
- `POST /auth/token` is rate limited (10 attempts per minute per username and per address) and answers 429 with `Retry-After`.
- `triage-service` and `assistant-service` are OAuth2 resource servers that validate tokens with the public key fetched from the JWKS endpoint (lazily, so they do not need `auth-service` at startup).
- Roles: `NURSE` (register patients) and `DOCTOR` (reports, assistant); both can change a patient's state (see ADR 0013). Reads of a patient and of the urgency view are open to both.
- Public: Actuator liveness and readiness. `/actuator/prometheus` needs HTTP Basic with the scraper credentials. Everything else requires a valid JWT.
- Input validation on every request; no credential is ever committed.

In production, identity would be delegated to a managed provider (Microsoft Entra ID or Keycloak) without changing the two resource servers.

---

## 9. Event contract (`triaje.eventos`)

Topic: `triaje.eventos`, 3 partitions, key = `pacienteId`. JSON value (field names in Spanish, as part of the external contract); headers: `correlationId` (the trace id), `traceparent` and `eventType`.

```json
{
  "idEvento": "6f9c0d0e-...",
  "tipo": "paciente_registrado",
  "version": 1,
  "ocurridoEn": "2026-11-18T15:30:00Z",
  "pacienteId": "3a1b...",
  "nivel": "CRITICO",
  "estadoAnterior": null,
  "estadoNuevo": "REGISTRADO",
  "slaMinutos": 5,
  "escalado": false
}
```

Event types: `paciente_registrado`, `paciente_en_consulta`, `paciente_alta`, `paciente_escalado`. Every event carries the patient level, so the assistant can count by level without calling Triage. Event records live in `triaxis-common`.

---

## 10. Cross-cutting concerns

**Errors.** Every service answers every error in the same RFC 7807 shape (ADR 0018): `type`, `title`, `status`, `detail`, `instance` (the path), `traceId` and `timestamp`; validation errors add `errores` (a list of `campo` and `mensaje`). The HTTP status line and the body field `status` come from the same value. Each service has one global `WebExceptionHandler` that extends the shared base class and only maps its own exceptions (functional endpoints have no controller advice); malformed bodies, wrong types (naming the field), oversized bodies (413), unsupported media types (415), unknown routes and unexpected errors (a generic 500) are handled by the base. 401 and 403 use the same writer through the shared entry point and denied handler. 429 comes from the token rate limit. Domain exceptions live in the domain layer and carry no HTTP knowledge.

**Observability (ADR 0019).**
- Traces: OpenTelemetry through Micrometer Tracing, with Reactor context propagation. The trace id is the correlation id: it is in every log line, in the error bodies, in the outbox row and in the Kafka headers `correlationId` and `traceparent`. Export to an OTLP endpoint is optional (`TRACING_EXPORT_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`); Jaeger runs in the local compose file.
- Logs: Spring Boot structured logging, ECS JSON on the console (plain text in the `local` profile). Logs go to standard output and are collected by the platform; they are not stored in the database (the clinical audit is a separate table). One access-log line per request (method, path, status, duration, user), never bodies or tokens.
- Metrics (Micrometer, Prometheus format, common tag `application`): `triage.classification` by level, `triage.outbox.pending`, `triage.outbox.published`, `triage.outbox.failures`, `triage.escalations` by level, `assistant.llm.latency` (tags `operation` `embedding` or `answer`, `outcome` `success`, `failure` or `rejected`), `assistant.answers` by outcome (`answered`, `no_protocol`, `rejected`, `degraded`), `assistant.urgency.events` by outcome (`applied`, `unchanged`, `invalid`) and the circuit breaker state from Resilience4j. `/actuator/prometheus` needs HTTP Basic (ADR 0020).
- Health: liveness state only for liveness; readiness of the triage service is the readiness state plus the database, and deliberately not Kafka (the outbox absorbs an outage). The assistant adds its database and Redis. Shutdown is graceful.

**Security hardening (ADR 0020).** Audience claim check, 64 KB body limit, rate limit on the token endpoint, strict JSON, protected metrics, no actuator endpoint except health, info and Prometheus, and no internal detail in answers or logs.

**Configuration (properties and environment).** Nothing secret lives in the repository; the `local` profile has development defaults only.

| Property or variable | Used by | Purpose |
|---|---|---|
| `triage.sla.minutes.*` | triage | SLA per level |
| `triage.escalation.enabled`, `triage.escalation.interval`, `triage.escalation.batch-size` | triage | Escalation loop (defaults `true`, `PT30S`, `100`) |
| `triage.administrative-complaints` | triage | Level 5 complaint list |
| `triage.outbox.relay.*`, `triage.outbox.topic.*` | triage | Relay loop and topic creation |
| `TRIAGE_DB_URL`, `TRIAGE_DB_USER`, `TRIAGE_DB_PASSWORD` | triage | PostgreSQL (R2DBC) |
| `KAFKA_BOOTSTRAP_SERVERS` | triage, assistant | Kafka |
| `AUTH_JWKS_URI` | triage, assistant | Public keys of the auth service |
| `JWT_PRIVATE_KEY` | auth | Signing key (PKCS#8 PEM); `auth.jwt.generate-key-if-missing` only in `local` |
| `auth.jwt.issuer`, `auth.jwt.audience`, `auth.jwt.ttl`, `auth.rate-limit.*` | auth | Token settings and rate limit |
| `triaxis.security.issuer`, `triaxis.security.audience` | triage, assistant | Token checks |
| `METRICS_USER`, `METRICS_PASSWORD` | all | Credentials of the metrics scraper |
| `TRACING_EXPORT_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `TRACING_SAMPLING_PROBABILITY` | all | OpenTelemetry export |
| `assistant.rag.top-k`, `assistant.rag.max-distance` | assistant | Retrieval tuning (phase 5) |
| `assistant.llm.provider` (`mock` by default), profile `gemini`, `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL`, `GEMINI_EMBEDDING_MODEL` | assistant | LLM selection, models and key (key from the environment only); without the `gemini` profile the Gemini auto-configuration is off (ADR 0008) |
| `assistant.rag.question-max-length`, `assistant.corpus.location`, `assistant.corpus.retry.*` | assistant | Question limit (1000), protocol folder, back-off of the corpus embedding |
| `assistant.llm.timeout.*`, `resilience4j.circuitbreaker.instances.llm.*` | assistant | Timeouts (embedding 5 s, answer 20 s, `Retry-After` 30 s) and circuit breaker of the LLM calls (ADR 0004) |
| `ASSISTANT_DB_URL`, `ASSISTANT_DB_USER`, `ASSISTANT_DB_PASSWORD` | assistant | PostgreSQL with pgvector (R2DBC) |
| `REDIS_HOST`, `REDIS_PORT`, `assistant.urgency.discharged-ttl`, `assistant.urgency.max-in-flight`, `assistant.urgency.redis-timeout` | assistant | Urgency view: Redis, discharged marker (30 days), records in flight (64), Redis timeout (2 s) |

**Backpressure.** Kafka consumers use `max.poll.records` and a bounded `flatMap` concurrency; the outbox relay works in batches; R2DBC pool sizes and WebClient timeouts are explicit.

---

## 11. Testing strategy

| Level | What | Tools |
|---|---|---|
| Domain unit | Classifier with E1 to E6 and boundary values, transition policy, SLA policy, complaint normalization | JUnit 5 |
| Use case | Use cases with fake ports | JUnit 5, `StepVerifier` |
| Architecture | Dependency rules between layers | ArchUnit |
| Persistence | Conditional updates, escalation SQL (idempotency, concurrency), report SQL | Testcontainers PostgreSQL |
| Messaging | Outbox publishes, consumer builds the Redis view, duplicate events | Testcontainers Kafka and Redis |
| Web | Status codes, validation messages, authorization per role | `WebTestClient.bindToRouterFunction`, `spring-security-test` |
| RAG | Citations present, no-match case without any LLM call, rejected invalid citation | Fake LLM and fake embeddings with controlled vectors |
| Resilience | Breaker opens after failures and the endpoint answers 503 | Fake failing LLM |
| Non-blocking | No blocking call on event-loop threads | BlockHound: a flow test in triage; in the assistant it is installed when the test JVM starts, so every test runs under it |

The LLM and embeddings are always faked in tests; no test calls a real provider.

Minimum coverage required: classification E1 to E6, valid and invalid transitions, escalation idempotency, RAG citations, and the "no match without hallucination" case.

---

## 12. Deployment

**Local:** one `docker-compose up` starts PostgreSQL (with pgvector, two databases created by an init script), Kafka (KRaft, topic created on startup), Redis, Jaeger (traces) and the three services. Default LLM profile is `mock`, so no API key is required.

**Kubernetes manifests (`deploy/k8s/`):** namespace; Deployments for the three services with liveness and readiness probes and resource requests and limits; PostgreSQL, Kafka and Redis (single replica, with persistent volumes where needed); Services; ConfigMap and Secrets; one Ingress with path-based routing (`/auth`, `/triage`, `/asistente`). The same manifests are validated locally on kind or minikube. Rules that already apply to the images and manifests: configuration only through environment variables (table in section 10), graceful shutdown, readiness without Kafka for triage, `/actuator` not routed by the Ingress, a Secret for the metrics credentials, the signing key and the database passwords, non-root containers and pinned base image tags.

**Azure (target, to confirm):** AKS with a single node, running the stateful pieces in the cluster to keep cost low (managed alternatives such as Event Hubs with a Kafka endpoint, Azure Cache for Redis and Azure Database for PostgreSQL cost more and add configuration). Images from a registry reachable by the cluster. Budget alert configured, resource group deleted after the evaluation. Costs and node sizing must be checked before choosing. Observability in Azure: Container Insights for the logs of standard output, a Prometheus scrape of `/actuator/prometheus` with the Secret credentials (Azure Monitor managed Prometheus or an in-cluster Prometheus), and an OTLP endpoint for traces (Application Insights or Jaeger); all optional for the deliverable and decided in the deployment ADR.

**CI (stretch):** GitHub Actions workflow that builds and runs the tests on every push.

---

## 13. Open decisions

| # | Decision | Current proposal | Alternative |
|---|---|---|---|
| 1 | Where and how alerts are sent (RF-06) | Kafka-driven notifier inside `triage-service`, webhook plus log adapter | Dedicated `notification-service`, email, SSE |
| 2 | Event publication | **Decided:** transactional outbox (ADR 0005) | Publish after commit with retry (faster to build) |
| 3 | Azure compute | AKS single node with in-cluster PostgreSQL, Kafka and Redis | Azure Container Apps, managed services |
| 4 | Level 5 complaint matching | **Decided:** normalized exact match (ASSUMPTIONS A3) | Contains match |

---

## 14. ADR plan

Status `written` means the file exists in `docs/adr/` (checked by `scripts/check-docs.sh`). ADRs are written when the decision is made, in the pull request that implements it.

| ADR | Decision | Status |
|---|---|---|
| 0001 | Spring Kafka instead of Reactor Kafka (discontinued) | written |
| 0002 | Custom R2DBC repository for vectors instead of `PgVectorStore` (blocking JDBC) | written |
| 0003 | Separate identity service with RS256 and JWKS | written |
| 0004 | Degraded mode: circuit breaker, 503, and Triage independence | written |
| 0005 | Transactional outbox for event publication | written |
| 0006 | Atomic escalation with conditional update and `SKIP LOCKED` | written |
| 0007 | Anti-hallucination strategy (retrieval gate, validated citations) | written |
| 0008 | Gemini for LLM and embeddings; Ollama and DeepSeek discarded | written |
| 0009 | Spring Boot 3.5 / Spring AI 1.1 out of OSS support, chosen on purpose | written |
| 0010 | Deployment strategy and cost control on Azure | pending (deployment) |
| 0011 | Alert channel for escalated patients | pending (alerts) |
| 0012 | Functional endpoints (`RouterFunction`) instead of annotated controllers | written |
| 0013 | Who may change a patient's state (`NURSE` and `DOCTOR`), an ambiguity of the specification | written |
| 0014 | Clinical audit trail: append-only table written in the same transaction, with the actor | written |
| 0015 | MapStruct for boundary mappers | written |
| 0016 | Hexagonal architecture enforced by ArchUnit and BlockHound tests | written |
| 0017 | Business validation lives in the domain | written |
| 0018 | Shared web module and a single error format | written |
| 0019 | Observability: OpenTelemetry traces, ECS logs, metrics and health | written |
| 0020 | API hardening: audience, body limit, rate limit and protected metrics | written |
| 0021 | Idempotent urgency view that tolerates missing, repeated and late events | written |

At least two are required; the more concrete ones (0001, 0002, 0006, 0007) are the priority.

---

## 15. Implementation order

Estimated against roughly 10 to 12 useful hours. Deliver something that runs end to end early, then deepen.

1. **Skeleton (1 h).** Gradle multi-module, `docker-compose` with PostgreSQL, Kafka and Redis, health endpoints, empty services starting.
2. **Auth (1 to 1.5 h).** Token endpoint, JWKS, shared `triaxis-security` resource-server configuration. Time-boxed.
3. **Triage core (2.5 h).** Classifier with tests E1 to E6, register and state endpoints, audit, validation, error handling.
4. **Events (1.5 h).** Outbox and relay (or direct publish), event contract.
5. **Escalation (1 h).** Scheduler, atomic SQL, idempotency tests.
6. **Assistant (2.5 h).** Corpus ingestion, retrieval, RAG with fakes, anti-hallucination, circuit breaker, urgency view from Kafka.
7. **Report SQL (45 min).**
8. **Alerts RF-06 (45 min).**
9. **Deployment (1.5 to 2 h).** Dockerfiles, Kubernetes manifests, first Azure deploy. Do a minimal smoke deploy before the final day.
10. **Docs (1 h).** README with diagram, ADRs, assumptions.

**Cut list, in order, if time runs out:** OpenTelemetry, CI workflow, outbox (fall back to direct publish), email/SSE alternatives. **Never cut:** E1 to E6 tests, escalation idempotency, RAG citations and the no-match case, circuit breaker with 503, Azure deployment, README and ADRs.
