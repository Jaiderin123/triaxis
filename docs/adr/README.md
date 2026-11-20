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
| 0003 | [Separate identity service with RS256 and JWKS](0003-identity-service-rs256-jwks.md) | Accepted |
| 0005 | [Transactional outbox for event publication](0005-transactional-outbox.md) | Accepted |
| 0009 | [Spring Boot 3.5 and Spring AI 1.1, chosen on purpose](0009-spring-boot-3-5-and-spring-ai-1-1.md) | Accepted |
| 0012 | [Functional endpoints instead of annotated controllers](0012-functional-endpoints.md) | Accepted |
| 0013 | [Who may change a patient's state](0013-state-change-roles.md) | Accepted |
| 0014 | [Clinical audit trail](0014-clinical-audit-trail.md) | Accepted |
| 0015 | [MapStruct for boundary mappers](0015-mapstruct-boundary-mappers.md) | Accepted |
| 0016 | [Hexagonal architecture enforced by tests](0016-hexagonal-architecture.md) | Accepted |
| 0017 | [Business validation lives in the domain](0017-validation-in-the-domain.md) | Accepted |
| 0018 | [Shared web module and a single error format](0018-shared-web-module-and-error-format.md) | Accepted |
| 0019 | [Observability: OpenTelemetry traces, ECS logs, metrics and health](0019-observability-opentelemetry-ecs-metrics.md) | Accepted |
| 0020 | [API hardening: audience, body limit, rate limit and protected metrics](0020-api-hardening.md) | Accepted |
