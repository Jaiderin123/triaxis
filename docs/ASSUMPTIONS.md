# Triaxis: Assumptions and Ambiguities

A log of every place where the requirements specification is silent or ambiguous, the decision taken, and what changes if the decision is wrong. "The requirements specification" is the original brief; [`ARCHITECTURE.md`](ARCHITECTURE.md) is the design that applies these decisions. Add new entries at the end and never reuse an ID.

A1 to A4 come from `ARCHITECTURE.md` section 6.2. A5 onward were added while preparing the plan. A15 and A21 were drafted and dropped before they were needed; their IDs are not reused.

---

## A1. Integer vital signs

- **Ambiguity:** the specification gives ranges such as 90 <= SpO2 <= 93 for level 2 and "SpO2 < 90" for level 1, without saying whether values like 93.5 are possible. Treated as real numbers, a gap appears between 93 and 94 (and likewise for systolic pressure between 100 and 101).
- **Decision:** heart rate, systolic, diastolic, SpO2, pain, GCS and age are integers. Decimals are rejected with 400. Temperature has one decimal.
- **Why:** it closes the classification gaps and matches how the measurements are recorded in practice.
- **Impact if wrong:** a client sending `93.5` gets 400 instead of a classification. Switching to decimals needs an explicit rule for the gaps and a schema change from `SMALLINT`.

## A2. GCS 14 and 15

- **Ambiguity:** level 2 covers GCS 9 to 13 and level 1 covers GCS <= 8; GCS 14 and 15 are not mentioned.
- **Decision:** GCS 14 and 15 do not trigger any level by themselves.
- **Why:** the specification lists conditions per level and 14 to 15 matches none of them.
- **Impact if wrong:** patients with GCS 14 would be classified one level too low.

## A3. Administrative complaint matching

- **Ambiguity:** the specification says the complaint "belongs to the list" without defining matching.
- **Decision:** normalized exact equality (lowercase, accents removed, trimmed, and runs of inner spaces reduced to one). The list is configurable (`triage.administrative-complaints`). Matching is not "contains".
- **Why:** a clinical complaint that merely mentions "seguimiento" must not be downgraded to level 5.
- **Impact if wrong:** a free-text complaint such as "renovacion de recetario cronico" would stay at level 4 instead of 5. Safe direction (higher priority), tracked as open decision 4 in `ARCHITECTURE.md`.

## A4. Validation ranges

- **Ambiguity:** none in the numbers, but not stated: which message format, and whether several errors are reported together.
- **Decision:** ranges exactly as in the specification table (HR 20-250, systolic 60-260, diastolic 30-160 and lower than systolic, SpO2 70-100, temperature 34.0-42.0, pain 0-10, GCS 3-15, age 0-120, name 3-100 characters). The error message names the invalid field. Violations are collected and returned together in a `ProblemDetail`.
- **Why:** meets "400 with a clear message indicating the invalid field".
- **Impact if wrong:** low; only the error body shape changes.

## A5. Request body field names

- **Ambiguity:** RF-01 lists the data (full name, age, heart rate, and so on) but not the JSON field names.
- **Decision:** Spanish camelCase names: `nombreCompleto`, `edad`, `frecuenciaCardiaca`, `presionSistolica`, `presionDiastolica`, `spo2`, `temperatura`, `dolor`, `gcs`, `quejaPrincipal`. Common variants are accepted with `@JsonAlias` (for example `nombre`, `fc`, `heartRate`). The canonical names are documented in the README.
- **Why:** the rest of the contract (`estado`, `nivel`, `slaMinutos`, `fechaRegistro`) is Spanish camelCase, and aliases tolerate an automated client using a different spelling.
- **Impact if wrong:** a client using an unlisted name gets 400. Adding an alias is a one-line change.

## A6. Meaning of "exceeded the SLA"

- **Ambiguity:** "superó su SLA" could mean elapsed >= SLA or elapsed > SLA.
- **Decision:** strictly greater: `registered_at + sla_minutes < now`. A patient exactly at the limit is not escalated yet.
- **Why:** "exceeded" is read literally; the scheduler runs periodically so the difference is at most one interval.
- **Impact if wrong:** an escalation could be one scheduler interval late in a boundary test. Change `<` to `<=` in the escalation `WHERE`.

## A7. Waiting time for patients discharged directly

- **Ambiguity:** waiting time is "registration to first entry into consultation". A patient moved `REGISTRADO -> ALTA` never enters consultation.
- **Decision:** they have no waiting time and are excluded from the average and p95. They still count in the totals and in the per-level counts.
- **Why:** the metric is defined only for consultation.
- **Impact if wrong:** averages would be lower or higher if direct discharges were measured to discharge time.

## A8. Hours without patients in the report

- **Ambiguity:** whether the hourly report lists hours with zero patients.
- **Decision:** such hours are omitted.
- **Why:** simpler SQL (`GROUP BY` only returns existing buckets) and no meaning for averages of empty sets.
- **Impact if wrong:** a client that expects a continuous series must fill the gaps; a `generate_series` join would add them.

## A9. Escalated patient that enters consultation

- **Ambiguity:** whether an escalated patient may still move to `EN_CONSULTA`.
- **Decision:** yes. The `escalado` flag stays true and the normal transitions apply. Escalation never changes the status, only the flag.
- **Why:** escalation is an alert, not a state; blocking consultation would harm the patient.
- **Impact if wrong:** low; adding a rule is a change in the transition policy.

## A10. Response when no protocol is found

- **Ambiguity:** the specification asks for an explicit answer that no protocol is available, without a status code or body.
- **Decision:** HTTP 200 with a fixed message in `respuesta` and `fuentes: []`. No LLM call and no generated clinical text.
- **Why:** it is a valid answer to the question, not a server or client error; it keeps the response shape stable for clients.
- **Impact if wrong:** a client expecting 404 would need a change in the handler only.

## A11. Model output that fails citation validation

- **Ambiguity:** what to return when the LLM answers but cites no excerpt, or cites an id that was not retrieved.
- **Decision:** the answer is discarded and replaced by the same fixed "no protocol available" notice with empty `fuentes`. A metric (`assistant.answers`, outcome `rejected`) counts it.
- **Why:** RN-05 forbids unsourced clinical content; returning the model text would break it.
- **Impact if wrong:** some valid answers may be dropped if the model formats ids badly; mitigated by structured output and by tests with the three sample questions.

## A12. Time zone and clock handling

- **Ambiguity:** not specified.
- **Decision:** all timestamps are stored as `TIMESTAMPTZ`, handled in UTC, and returned in ISO 8601 with `Z`. The R2DBC session time zone is UTC, set by a connection customizer, and the tests run in a non-UTC JVM (ADR 0022). Time always comes from a `Clock` port. Hourly buckets are UTC hours.
- **Why:** removes daylight-saving and server-locale effects and makes SLA tests deterministic.
- **Impact if wrong:** clients in Colombia (UTC-5) see buckets shifted by whole hours; since the offset is a whole number of hours, bucket boundaries still align.

## A13. Event message format and versioning

- **Ambiguity:** the specification leaves the format free ("JSON, Avro, etc.; document it").
- **Decision:** JSON on topic `triaje.eventos` (3 partitions, key = patient id). Field names are Spanish (`idEvento`, `tipo`, `version`, `ocurridoEn`, `pacienteId`, `nivel`, `estadoAnterior`, `estadoNuevo`, `slaMinutos`, `escalado`). `version: 1` is in every payload; headers `correlationId`, `traceparent` and `eventType` (escalation events only `eventType`, A31). Delivery is at least once and consumers are idempotent. Every event carries the patient level so the urgency view needs no extra call.
- **Why:** simple to inspect and test, no schema registry needed.
- **Impact if wrong:** moving to Avro needs a registry and a new module dependency; the `version` field leaves room for compatible evolution.

## A14. Alert channel for RF-06

- **Ambiguity:** channel, format and technology are deliberately unspecified.
- **Decision:** a Kafka-driven `AlertNotifier` inside `triage-service`: webhook (Slack or Teams-compatible JSON) as the real adapter, structured log as the default. Retries with backoff; failures are counted and never affect escalation.
- **Why:** decoupled from the escalation transaction, no fourth service, testable with a fake webhook. Alternatives (email, SSE, a separate `notification-service`) are in `ARCHITECTURE.md` 6.8.
- **Impact if wrong:** replacing the adapter does not touch the domain. The full reasoning is in ADR 0011; the content is in A44 and the delivery in A45.

## A16. LLM profile in the deployed environment

- **Ambiguity:** the specification asks for a deployed, reachable system but does not say whether it must use a real LLM.
- **Decision:** local and test runs use the `mock` profile. The deployed environment uses Gemini (`gemini` profile) with `GEMINI_API_KEY` from a Kubernetes Secret (decided while planning phase 8, ADR 0010); when Gemini fails, the degraded mode answers 503 (ADR 0004), and going back to `mock` is a change of the profile only.
- **Why:** a real model shows the RAG working, but adds a secret, cost and a failure point.
- **Impact if wrong:** a client may expect real generated answers; or a missing key may break the demo.

## A17. Who may change a patient's state

- **Ambiguity:** the specification asks for authentication but not for roles. In practice a doctor starts a consultation.
- **Decision:** `NURSE` and `DOCTOR` may both change a patient's state. `NURSE` registers patients; `DOCTOR` reads reports and uses the assistant; reads of a patient and of the urgency view are open to both.
- **Why:** the doctor is who starts a consultation, so restricting `PATCH` to nurses would block the real flow.
- **Impact if wrong:** a stricter policy is a one-line rule change in the security filter chain. Recorded in ADR 0013.

## A18. Authentication scope

- **Ambiguity:** "basic authentication (JWT or API key)" without details.
- **Decision:** JWT RS256 from a separate `auth-service`, short-lived (15 minutes), no refresh tokens, no registration. Request fields are `username` and `password`; the response follows OAuth2 names (`access_token`, `token_type`, `expires_in`) because authentication is not part of the Spanish domain contract. Bad credentials return 401 with a generic message. Users come from configuration with BCrypt hashes; the repository only holds demonstration users for the local profile. The signing key comes from `JWT_PRIVATE_KEY`, and a key is generated only when `auth.jwt.generate-key-if-missing=true`. `auth-service` does not depend on `triaxis-security`.
- **Why:** shows the validation path through JWKS without an external identity provider, and avoids shared secrets. See ADR 0003.
- **Impact if wrong:** none for the current scope; the resource servers do not change when the issuer changes. Switching to Spanish field names is a DTO change.

## A19. Urgency view counts

> **Superseded by A33** (ADR 0021). Kept for history.

- **Ambiguity:** "active" patients are "all that are not in ALTA", but the view is built from events only.
- **Decision:** `paciente_registrado` increments the level; `paciente_alta` decrements it; `paciente_en_consulta` and `paciente_escalado` do not change counts. The level never changes after registration, so the stored level is used on discharge. Unknown patients in a discharge event are ignored.
- **Why:** keeps the Lua script small and idempotent.
- **Impact if wrong:** if reclassification is added later, a `paciente_reclasificado` event type would be required.

## A20. Dependencies outside the original stack

- **Ambiguity:** `ARCHITECTURE.md` uses ArchUnit and BlockHound and tests need Redis, but `TECH_STACK.md` did not list them.
- **Decision:** adopted. ArchUnit 1.5.1 and BlockHound 1.0.18.RELEASE are pinned in the version catalog (neither is managed by the Spring Boot BOM). Redis in tests uses a Testcontainers generic container, so no new library. `TECH_STACK.md` section 8 was updated.
- **Why:** they prove the hexagonal rules and the "no blocking in the event loop" requirement automatically.
- **Impact if wrong:** removing them only affects the architecture and non-blocking tests.

## A22. Audit records the author of each change

- **Ambiguity:** RN-04 lists the fields to record but not who made the change.
- **Decision:** `patient_audit` also stores `actor`: the JWT `sub` of the caller, or `system` for the escalation scheduler. See ADR 0014.
- **Why:** both roles can change a patient's state (ADR 0013) and a clinical audit without an author is of little use.
- **Impact if wrong:** an extra column; it is easy to ignore if it is not wanted.

## A23. Validation lives in the domain

- **Ambiguity:** where the RF-01 validation rules are enforced.
- **Decision:** the domain validates registration data and reports every violation with its field name; the web layer only rejects malformed JSON and wrong types (decimals where integers are expected). Database `CHECK` constraints are a second safety net. The 400 response uses the external (Spanish) field names.
- **Why:** one source of truth, no framework in the domain.
- **Impact if wrong:** moving to Bean Validation in the DTOs would duplicate the rules.

## A24. Chief complaint length

- **Ambiguity:** the specification gives a range for every field except the chief complaint, which is only "text".
- **Decision:** required, non-blank, at most 500 characters.
- **Why:** an unbounded text field invites abuse; 500 characters are enough for a complaint.
- **Impact if wrong:** a longer complaint is rejected with 400 naming `quejaPrincipal`.

## A25. Response shapes of the triage API

- **Ambiguity:** the specification names a few response fields and leaves the rest open.
- **Decision:** `POST` returns 201 with `id`, `nivel`, `slaMinutos`, `estado`, `fechaRegistro` and a `Location` header. `GET` returns the full patient (request fields plus `nivel`, `slaMinutos`, `estado`, `escalado`, `fechaRegistro`, `fechaCambioEstado` and `tiempoEnEstadoSegundos`). `PATCH` returns 200 with the same body as `GET`. Field names stay in Spanish, so the JSON never mixes languages. Error bodies are standard `ProblemDetail` (English text); validation errors add an `errores` list with `campo` (the external field name) and `mensaje`.
- **Why:** consistent contract, and the time in the current state is exact enough to test.
- **Impact if wrong:** a client expecting 204 on `PATCH` ignores the body; renaming is a DTO change.

## A26. Correlation of a request

- **Ambiguity:** the specification asks for structured logs with correlation but not for where the id comes from.
- **Decision:** the correlation id is the OpenTelemetry trace id. A request that carries a valid W3C `traceparent` header continues that trace; otherwise a new trace starts. The trace id appears in every log line, in the `traceId` of error bodies, in the outbox row and in the Kafka headers `correlationId` and `traceparent`. This replaces the earlier `X-Correlation-Id` header, which was removed. See ADR 0019.
- **Why:** one mechanism that also works across services and Kafka, and that covers the optional tracing requirement.
- **Impact if wrong:** callers that sent `X-Correlation-Id` get a new trace instead; tracing is independent of business behavior.

## A27. Standard error format

- **Ambiguity:** the specification asks for a clear message for invalid fields and for 409, 503 and similar answers, but not for a common shape.
- **Decision:** every error of every service is RFC 7807 with `type`, `title`, `status`, `detail`, `instance`, `traceId` and `timestamp`; validation errors add `errores` with `campo` and `mensaje`. 401 and 403 use the same shape. Messages are in English, field names are the external ones. See ADR 0018.
- **Why:** clients handle one format, and a trace id makes any error traceable.
- **Impact if wrong:** a different shape is a change in one class of the shared module.

## A28. Hardening values

- **Ambiguity:** the specification asks for authentication and input validation only.
- **Decision:** tokens carry and require `aud` = `triaxis`; request bodies are limited to 64 KB; the token endpoint allows 10 attempts per minute per username and per address; sensitive data is never logged. See ADR 0020.
- **Why:** reasonable defaults that protect the API without making it hard to use.
- **Impact if wrong:** the values are configuration (`auth.jwt.audience`, `spring.codec.max-in-memory-size`, `auth.rate-limit.*`). Known limit: the attempts are counted before the password is checked, and behind a proxy every client shares one address, so the limit per address becomes global. Counting only failures and the forwarded address of a trusted proxy is a task of phase 8.

## A29. Protected metrics endpoint

- **Ambiguity:** the specification asks for Micrometer metrics, not for how they are exposed or protected.
- **Decision:** `/actuator/prometheus` is exposed with HTTP Basic using `METRICS_USER` and `METRICS_PASSWORD` from the environment; without a password it is denied. The ingress of the deployment must not route `/actuator` (phase 8). Only health, info and Prometheus are exposed.
- **Why:** metrics reveal internals, so they are not left open, and a scraper can use Basic credentials from a Secret.
- **Impact if wrong:** a management port or a network-level restriction can replace it without changing the metrics.

## A30. Readiness of the triage service

- **Ambiguity:** which dependencies make the service not ready.
- **Decision:** readiness includes the database and excludes Kafka, because the outbox keeps events when Kafka is down and the service can still register patients.
- **Why:** a broker outage must not take every pod out of service.
- **Impact if wrong:** adding Kafka to the readiness group is a one-line property change.

## A31. Trace of an escalation

- **Ambiguity:** the correlation id of an event is the trace id of the request that caused it (A26), but an escalation is started by the scheduler, not by a request.
- **Decision:** escalation events have no request trace: the outbox row has no `correlation_id` or `traceparent`, and the Kafka message has only the `eventType` header. The audit entry records the actor `system`, and the scheduler logs how many patients it escalated.
- **Why:** inventing a trace per cycle adds code without a caller to correlate with; the patient id (the message key) already links the escalation to the earlier events.
- **Impact if wrong:** a consumer that requires `correlationId` must accept it as optional. Starting an observation per cycle would add a trace without changing the contract.

## A32. Statuses in the escalation event and audit entry

- **Ambiguity:** the event contract has `estadoAnterior` and `estadoNuevo`, but an escalation does not change the status.
- **Decision:** both are the current status (`REGISTRADO`), and `escalado` is `true`. The audit entry also has the same previous and new status. Escalation never changes the status or the time of the last status change (A9).
- **Why:** the fields stay non-null and true; a consumer can tell the escalation by `tipo` alone.
- **Impact if wrong:** a consumer that expects `estadoAnterior` to be null for escalations needs a one-line change in the domain event factory.

## A33. Urgency view counts (replaces A19)

- **Ambiguity:** "active" patients are all that are not in `ALTA`, but the view is built from events only, and the specification does not say how it behaves with repeated, missing or late events. Our delivery is at least once (ADR 0005), and a rebuild replays the topic.
- **Decision:** any event other than `paciente_alta` stores an unknown patient as active with the level of the event and adds one; `paciente_alta` marks the patient as discharged (subtracting one if it was active), and discharged is final; events of a known patient change nothing else. The discharged marker expires after 30 days, longer than the topic retention. See ADR 0021.
- **Why:** the first rule (A19) did not count a patient whose registration was never seen, and could count a discharged patient again after a repeated registration. The new rule gives the right count in both cases.
- **Impact if wrong:** a patient discharged and then registered again with the same id would not be counted; triage never reuses ids, so this cannot happen. Reclassification would need a new event type.

## A34. Question validation and the fixed notice

- **Ambiguity:** the specification asks for an explicit "no protocol available" answer, and for input validation, without the exact text or limits.
- **Decision:** `pregunta` is required, not blank, and at most 1000 characters (400 naming `pregunta` otherwise). The fixed notice is `No existe un protocolo disponible para esta pregunta.`, with `fuentes: []` and HTTP 200 (A10). The same notice replaces an answer whose citations fail validation (A11).
- **Why:** a bounded question keeps the prompt and the cost under control; one fixed text is easy to test and never contains clinical content.
- **Impact if wrong:** the limit and the text are configuration and a constant.

## A35. Response of the urgency view

- **Ambiguity:** the specification asks for the number of active patients per level without a JSON shape.
- **Decision:** `{ "porNivel": { "CRITICO": n, "URGENTE": n, "URGENTE_2": n, "NO_URGENTE": n, "NO_URGENTE_2": n }, "total": n, "actualizadoEn": "..." }`. All five levels are always present (zeros included); `actualizadoEn` is the time of the newest applied event, null when none.
- **Why:** a client reads a level directly by its name; `actualizadoEn` shows how fresh the view is.
- **Impact if wrong:** a different shape is a DTO change.

## A36. Similarity threshold per embedding model

- **Ambiguity:** the threshold must be calibrated with the embedding model, and it is different for each one.
- **Decision:** one `assistant.rag.max-distance` per provider. `mock` (bag of words): 0.70; the relevant sections of the sample questions are at 0.47 and 0.60 and the closest section to the diabetes question at 0.82. `gemini` (`gemini-embedding-001`, 768 dimensions, task type `SEMANTIC_SIMILARITY`): 0.33, calibrated on 2026-11-19 with ten questions: six covered by the corpus (the two samples plus contraindications, qSOFA criteria and pediatric rehydration) are at most 0.29 from their section; four outside it (diabetes 0.45, myocardial infarction 0.38, paracetamol dose 0.38, asthma attack 0.37) are at least 0.37 away.
- **Why:** the gate must refuse questions without a protocol before any model call; the value sits in the middle of the gap.
- **Impact if wrong:** the gap is narrow (0.29 to 0.37), so a question close to the corpus but not covered by it may pass the gate; the prompt rule (`NO_PROTOCOL`) and the citation check still stop an invented answer. A new embedding model needs a new calibration; the threshold is a property.

## A37. Loading the corpus at startup

- **Ambiguity:** the specification asks for RAG over the corpus and a 503 when the LLM is unavailable, but not what happens when the provider is unavailable while the service starts, or when a protocol file is broken.
- **Decision:** the protocol files are read and validated on the main thread before the service accepts traffic; a malformed or empty corpus stops the startup. The embedding of the sections runs in the background and is retried with back-off (2 seconds up to 1 minute, `assistant.corpus.retry.*`), one section at a time. Until the corpus is stored with the current embedding model, `POST /asistente/consulta` answers 503, never the "no protocol" notice. Readiness does not wait for the embedding.
- **Why:** a broken corpus is a deployment error and must be seen at once; an unavailable provider is transient and must not take down the urgency view, which does not depend on the LLM. Answering "no protocol" while the corpus is missing would be false.
- **Impact if wrong:** a deployment with a bad key starts and keeps retrying (visible in the logs and in the 503 answers) instead of failing; making readiness wait for the corpus is a health indicator change.

## A38. Invalid records and rebuilds of the urgency view

- **Ambiguity:** the specification says the view is built from the topic, not what to do with a record that is not a valid event, or when to rebuild the view.
- **Decision:** a record that cannot be read as an event, or has no patient, level, type or time, is logged without its payload, counted (`assistant.urgency.events`, outcome `invalid`) and acknowledged, so it never blocks the partition. A Redis failure is retried with back-off until it works, so no valid event is lost. The view is rebuilt from the beginning of the topic only when the consumer gets its partitions and the store has no `urgency:initialized` key (written only by the reset before a replay, ADR 0022), that is at startup or after a restart with a new or lost Redis.
- **Why:** a poison message must not stop the view for every later event; a valid event must never be dropped. With asynchronous acknowledgments a seek while records are in flight does not replay them, so the rebuild happens before any record is processed.
- **Impact if wrong:** a Redis lost while the service runs shows only the events received after the loss until the next restart; a restart (or a rolling restart in Kubernetes) rebuilds it, even if events arrived in between. When Redis does not answer at partition assignment, the consumer waits there and consumes nothing until it answers. The first start with the `urgency:initialized` marker rebuilds an existing view once. An invalid event is not counted, which only happens if the contract is broken.

## A39. The assistant runs as a single replica

- **Ambiguity:** the deployment can run several replicas of each service, but the rebuild of the urgency view (ADR 0021, A38) empties the shared Redis view and replays only the partitions assigned to the consumer that rebuilds it.
- **Decision:** the assistant runs as **one replica** (`replicas: 1` in the Kubernetes manifests of phase 8). Triage and auth may run several. A negative count, which only a broken store or concurrent rebuilds could produce, is read as zero, logged and counted (`assistant.urgency.negative.counts`) instead of failing the read.
- **Why:** coordinating a rebuild across replicas needs a distributed lock and an atomic swap of the view; one replica is enough for the expected load, and a restart takes seconds.
- **Impact if wrong:** with two replicas, a rebuild can drop or double count the patients of some partitions until the next rebuild. Scaling out needs a coordinated rebuild first (listed in the improvements of `PLAN.md`).

## A40. The SLA is fixed when the patient is registered

- **Ambiguity:** the SLA per level is configurable (RN-02), but the specification does not say whether a new value applies to patients who are already waiting.
- **Decision:** the SLA in minutes is stored with each patient at registration (`patients.sla_minutes`); a change of `triage.sla.minutes.*` applies to new patients only.
- **Why:** the escalation of a patient must not move because the configuration changed while the patient waited, and the event and the audit keep the value that was applied.
- **Impact if wrong:** applying a new SLA to waiting patients needs the escalation statement to read the current value per level instead of the stored one.

## A41. Response of the operation report

- **Ambiguity:** RF-05 lists the data per hour but not the JSON shape.
- **Decision:** an object with the range asked (`desde`, `hasta`, as UTC instants) and `horas`, a list ordered by hour. Each hour has `hora` (UTC, ISO 8601 with `Z`), `totalPacientes`, `porNivel` with the five levels (zeros included), `esperaPromedioMinutos`, `esperaP95Minutos`, `totalAcumulado` (running total over the range) and `rankingCarga` (rank by number of patients, ties share the rank). See `ARCHITECTURE.md` section 6.7.
- **Why:** echoing the range shows how dates were interpreted; an object can grow (for example totals of the range) without breaking clients; `porNivel` is the same shape as the urgency view.
- **Impact if wrong:** a client that expects a bare list reads `horas`; renaming fields is a DTO change.

## A42. Range of the operation report

- **Ambiguity:** RF-05 asks for a "range of ISO 8601 dates" without saying which forms, whether the end is included, or any limit.
- **Decision:** both parameters are required. Each one is either a date (`2026-11-19`, a whole UTC day: `desde` is its start and `hasta` includes it, so it means the start of the next day) or an instant with a zone (`2026-11-19T15:00:00Z`, `2026-11-19T10:00:00-05:00`). A date-time without a zone is rejected with 400 because it is ambiguous. The range is half-open (`desde` included, `hasta` excluded), must not be empty or inverted, and is at most `triage.report.max-range` (31 days, at most 744 hours). Errors are 400 naming `desde` or `hasta`.
- **Why:** dates cover the common use (a day or a week) and instants give full control; a zone avoids guessing between UTC and Colombia; the limit keeps the query bounded.
- **Impact if wrong:** a client sending a local date-time gets 400 and must add the zone; a longer range must be split or the property raised.

## A43. Waiting time in the operation report

- **Ambiguity:** RF-05 defines the wait as registration to first entry into consultation, but not which hour a patient belongs to, nor what to show when nobody entered consultation.
- **Decision:** a patient counts in the UTC hour of its registration. The wait is `consultation_started_at - registered_at` in minutes; patients still waiting and patients discharged directly (A7) are counted in `totalPacientes` and `porNivel` but not in the waits. When no patient of an hour entered consultation, both waits are `null`. The p95 is `percentile_cont(0.95)` (interpolated). Waits are rounded to 2 decimals.
- **Why:** grouping by registration keeps one hour per patient and matches the totals; `null` is not a zero wait; the interpolated percentile is stable with few patients.
- **Impact if wrong:** grouping by the hour of consultation would move patients between hours; `percentile_disc` would return a real wait instead of an interpolated one.

## A44. Content of the escalation alert

- **Ambiguity:** RF-06 does not say what the alert contains, and a webhook (for example to Slack) sends the data outside the system.
- **Decision:** the alert carries the minimum: the patient id, the level, the SLA in minutes, the minutes waited from the registration to the escalation, the time of the escalation, and the path `/triage/pacientes/{id}` to read the rest with authentication. It carries no name, vital signs or complaint. The `text` is in Spanish, like the rest of the external contract.
- **Why:** the team knows how urgent the case is and where to look, and no identifiable clinical data leaves the system through a third-party channel.
- **Impact if wrong:** the team must open the patient to see the name; adding fields to the alert is a change in the notifier only.

## A45. Delivery of the escalation alert

- **Ambiguity:** Kafka delivers at least once, and a channel can be down; RF-06 says nothing about duplicates or failures.
- **Decision:** every handled event is recorded in `alert_deliveries` (event id as the key) after it was sent or given up, and an event already recorded is not sent again (counted as `duplicate`). A send is tried up to 3 times in total, waiting 1 s and then 2 s between the attempts; then the alert is counted as `failed`, logged without patient data, and the next event goes on. The escalation itself is never affected.
- **Why:** repeated alerts erode trust in the channel; one channel outage must not hold every later alert.
- **Impact if wrong:** an alert that failed three times is not sent again (it stays in the logs, the metric and the table); a crash after the send and before the record can still repeat one alert.
- **Later:** a patient that no longer waits when the event is handled is recorded as `SKIPPED` and not alerted (A46, ADR 0023).


## A46. Old and late escalation events

- **Ambiguity:** the alert listener reads `triaje.eventos` in its own consumer group. When the group starts for the first time (a first deployment, or offsets lost or expired) it must choose where to start, and an event can also be handled long after it happened (the listener was down). RF-06 says nothing about old events.
- **Decision:** the group starts from the oldest retained event (`auto.offset.reset=earliest`), so no escalation is lost. Before sending, the patient is read: a patient that no longer waits (in consultation or discharged) is not alerted and the event is recorded as `SKIPPED`; a patient that still waits is alerted however old the event is (ADR 0023).
- **Why:** the manual check of phase 7 showed that a new group replays the retained escalations, including patients already attended; an alert must reach the team only while it can still help.
- **Impact if wrong:** a patient attended while the listener was down gets no alert at all (intended), and the status is read at handling time, so a patient that enters consultation a moment later is still alerted.

## A47. The deployed environment is a demonstration

- **Ambiguity:** the specification asks for a deployment on Azure with the APIs reachable, but says nothing about availability, backups or the size of the load.
- **Decision:** one AKS node, one replica of each service, and PostgreSQL, Kafka and Redis in the cluster on single persistent volumes, with no backups. The cluster is stopped outside the demonstrations and the resource group is deleted when the evaluation ends (ADR 0010).
- **Why:** it shows the system running on Kubernetes at the lowest cost; high availability would multiply the nodes and the managed services.
- **Impact if wrong:** a lost node or volume loses the data, and a stopped cluster does not answer until it is started. Moving to managed services changes the connection settings only.

## A48. Public address of the deployment

- **Ambiguity:** "a reachable URL" does not say which host name or whether it uses TLS.
- **Decision:** the host name is the free DNS label of the public IP in Azure (`<label>.<region>.cloudapp.azure.com`), served over HTTPS with a Let's Encrypt certificate from cert-manager. The paths are those of the local run (`/auth`, `/.well-known/jwks.json`, `/triage`, `/asistente`); `/actuator` is not public. The issuer of the tokens stays `triaxis-auth` (a name, not an address), and the services read the JWKS from auth inside the cluster (`AUTH_JWKS_URI`), without leaving it.
- **Why:** the tokens must not travel in clear over the internet, and no domain has to be bought.
- **Impact if wrong:** a custom domain is a change of the overlay and the certificate only.
