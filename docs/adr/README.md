# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) of Triaxis.

## Process

1. Copy [0000-template.md](0000-template.md) to `NNNN-short-title.md`, using the next free number.
2. Fill in every section and set the status to `Proposed`.
3. Once agreed, change the status to `Accepted`. Never delete an ADR; mark it `Superseded by NNNN` instead.

Ambiguities in the requirements specification that change behavior are recorded as ADRs, together with the smaller entries in [../ASSUMPTIONS.md](../ASSUMPTIONS.md).

## Index

| # | Title | Status |
|---|---|---|
| 0000 | [Template](0000-template.md) | n/a |
| 0001 | [Spring Kafka instead of Reactor Kafka](0001-spring-kafka-instead-of-reactor-kafka.md) | Accepted |
| 0002 | [Custom R2DBC repository for vectors instead of `PgVectorStore`](0002-vectors-over-r2dbc.md) | Accepted |
| 0003 | [Separate identity service with RS256 and JWKS](0003-identity-service-rs256-jwks.md) | Accepted |
| 0004 | [Degraded mode of the assistant: circuit breaker, 503 and triage independence](0004-degraded-mode.md) | Accepted |
| 0005 | [Transactional outbox for event publication](0005-transactional-outbox.md) | Accepted |
| 0006 | [Atomic escalation with a conditional update and `SKIP LOCKED`](0006-atomic-escalation.md) | Accepted |
| 0007 | [Anti-hallucination strategy: retrieval gate, constrained prompt and validated citations](0007-anti-hallucination.md) | Accepted |
| 0008 | [Gemini for answers and embeddings, with a deterministic `mock` provider by default](0008-gemini-and-mock-provider.md) | Accepted |
| 0009 | [Spring Boot 3.5 and Spring AI 1.1, chosen on purpose](0009-spring-boot-3-5-and-spring-ai-1-1.md) | Accepted |
| 0010 | [Deployment on a single-node AKS cluster, with the state in the cluster and the cost under control](0010-deployment-on-aks.md) | Accepted (implemented in phase 8) |
| 0011 | [Alerts to the medical team: a Kafka listener with a log or webhook notifier](0011-alert-channel.md) | Accepted (delivery partly superseded by 0023) |
| 0012 | [Functional endpoints instead of annotated controllers](0012-functional-endpoints.md) | Accepted |
| 0013 | [Who may change a patient's state](0013-state-change-roles.md) | Accepted |
| 0014 | [Clinical audit trail](0014-clinical-audit-trail.md) | Accepted |
| 0015 | [MapStruct for boundary mappers](0015-mapstruct-boundary-mappers.md) | Accepted |
| 0016 | [Hexagonal architecture enforced by tests](0016-hexagonal-architecture.md) | Accepted |
| 0017 | [Business validation lives in the domain](0017-validation-in-the-domain.md) | Accepted |
| 0018 | [Shared web module and a single error format](0018-shared-web-module-and-error-format.md) | Accepted |
| 0019 | [Observability: OpenTelemetry traces, ECS logs, metrics and health](0019-observability-opentelemetry-ecs-metrics.md) | Accepted |
| 0020 | [API hardening: audience, body limit, rate limit and protected metrics](0020-api-hardening.md) | Accepted |
| 0021 | [Idempotent urgency view that tolerates missing, repeated and late events](0021-idempotent-urgency-view.md) | Accepted |
| 0022 | [Deny by default, bounded waits and UTC sessions](0022-deny-by-default-and-bounded-waits.md) | Accepted |
| 0023 | [Alerts only for patients that still wait](0023-alerts-only-for-waiting-patients.md) | Accepted |
| 0024 | [A database role per service, and the schema applied outside the startup](0024-database-roles-and-schema-outside-startup.md) | Accepted (implemented in phase 8) |
