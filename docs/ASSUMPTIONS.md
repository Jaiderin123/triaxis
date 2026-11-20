# Triaxis: Assumptions and Ambiguities

A log of every place where the requirements specification is silent or ambiguous, the decision taken, and what changes if the decision is wrong. "The requirements specification" is the original brief; [`ARCHITECTURE.md`](ARCHITECTURE.md) is the design that applies these decisions. Add new entries at the end and never reuse an ID.

A1 to A4 come from `ARCHITECTURE.md` section 6.2. A5 onward were added while preparing the plan.

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
- **Decision:** normalized exact equality (lowercase, accents removed, trimmed). The list is configurable (`triage.administrative-complaints`). Matching is not "contains".
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
- **Decision:** all timestamps are stored as `TIMESTAMPTZ`, handled in UTC, and returned in ISO 8601 with `Z`. The R2DBC session time zone is UTC. Time always comes from a `Clock` port. Hourly buckets are UTC hours.
- **Why:** removes daylight-saving and server-locale effects and makes SLA tests deterministic.
- **Impact if wrong:** clients in Colombia (UTC-5) see buckets shifted by whole hours; since the offset is a whole number of hours, bucket boundaries still align.

## A13. Event message format and versioning

- **Ambiguity:** the specification leaves the format free ("JSON, Avro, etc.; document it").
- **Decision:** JSON on topic `triaje.eventos` (3 partitions, key = patient id). Field names are Spanish (`idEvento`, `tipo`, `version`, `ocurridoEn`, `pacienteId`, `nivel`, `estadoAnterior`, `estadoNuevo`, `slaMinutos`, `escalado`). `version: 1` is in every payload; headers `correlationId` and `eventType`. Delivery is at least once and consumers are idempotent. Every event carries the patient level so the urgency view needs no extra call.
- **Why:** simple to inspect and test, no schema registry needed.
- **Impact if wrong:** moving to Avro needs a registry and a new module dependency; the `version` field leaves room for compatible evolution.

## A14. Alert channel for RF-06

- **Ambiguity:** channel, format and technology are deliberately unspecified.
- **Decision:** a Kafka-driven `AlertNotifier` inside `triage-service`: webhook (Slack or Teams-compatible JSON) as the real adapter, structured log as the default. Retries with backoff; failures are counted and never affect escalation.
- **Why:** decoupled from the escalation transaction, no fourth service, testable with a fake webhook. Alternatives (email, SSE, a separate `notification-service`) are in `ARCHITECTURE.md` 6.8.
- **Impact if wrong:** replacing the adapter does not touch the domain. The full reasoning goes to ADR 0011 (not written yet).

## A16. LLM profile in the deployed environment

- **Ambiguity:** the specification asks for a deployed, reachable system but does not say whether it must use a real LLM.
- **Decision:** not decided. Local and test runs use the `mock` profile. For the deployed environment either keep `mock` (no key, no cost, deterministic) or enable Gemini through a Kubernetes Secret. To be chosen in phase 8 and recorded in ADR 0008 or 0010.
- **Why:** a real model shows the RAG working, but adds a secret, cost and a failure point.
- **Impact if wrong:** a reviewer may expect real generated answers; or a missing key may break the demo.

## A17. Who may change a patient's state

- **Ambiguity:** the specification asks for authentication but not for roles. In practice a doctor starts a consultation.
- **Decision:** `NURSE` and `DOCTOR` may both change a patient's state. `NURSE` registers patients; `DOCTOR` reads reports and uses the assistant; reads of a patient and of the urgency view are open to both.
- **Why:** the doctor is who starts a consultation, so restricting `PATCH` to nurses would block the real flow.
- **Impact if wrong:** a stricter policy is a one-line rule change in the security filter chain. Recorded in ADR 0013.

## A18. Authentication scope

- **Ambiguity:** "basic authentication (JWT or API key)" without details.
- **Decision:** JWT RS256 from a separate `auth-service`, short-lived (15 minutes), no refresh tokens, no registration. Request fields are `username` and `password`; the response follows OAuth2 names (`access_token`, `token_type`, `expires_in`) because authentication is not part of the Spanish domain contract. Bad credentials return 401 with a generic message. Users come from configuration with BCrypt hashes; the repository only holds demonstration users for the local profile. The signing key comes from `JWT_PRIVATE_KEY`, and a key is generated only when `auth.jwt.generate-key-if-missing=true`. `auth-service` does not depend on `triaxis-security`.
- **Why:** shows the validation path through JWKS without an external identity provider, and avoids shared secrets. See ADR 0003.
- **Impact if wrong:** none for the evaluation; the resource servers do not change when the issuer changes. Switching to Spanish field names is a DTO change.

## A19. Urgency view counts

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
- **Impact if wrong:** an extra column; it is easy to ignore if a reviewer does not want it.

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
- **Impact if wrong:** the values are configuration (`auth.jwt.audience`, `spring.codec.max-in-memory-size`, `auth.rate-limit.*`).

## A29. Protected metrics endpoint

- **Ambiguity:** the specification asks for Micrometer metrics, not for how they are exposed or protected.
- **Decision:** `/actuator/prometheus` is exposed with HTTP Basic using `METRICS_USER` and `METRICS_PASSWORD` from the environment; without a password it is denied. The ingress does not route `/actuator`. Only health, info and Prometheus are exposed.
- **Why:** metrics reveal internals, so they are not left open, and a scraper can use Basic credentials from a Secret.
- **Impact if wrong:** a management port or a network-level restriction can replace it without changing the metrics.

## A30. Readiness of the triage service

- **Ambiguity:** which dependencies make the service not ready.
- **Decision:** readiness includes the database and excludes Kafka, because the outbox keeps events when Kafka is down and the service can still register patients.
- **Why:** a broker outage must not take every pod out of service.
- **Impact if wrong:** adding Kafka to the readiness group is a one-line property change.
