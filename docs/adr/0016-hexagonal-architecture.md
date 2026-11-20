# 0016. Hexagonal architecture enforced by tests

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The requirements specification leaves the internal architecture to the author. The services have business rules (classification, state flow, escalation, anti-hallucination) that must stay testable without databases, brokers or an LLM provider, and the infrastructure (R2DBC, Kafka, Redis, Gemini) can change.

## Decision

- Each service uses **ports and adapters** with three layers under `dev.triaxis.<service>`:
  - `domain` depends on the JDK only (model, rules, policies, exceptions);
  - `application` depends on the domain only (use cases and the ports they need), without Spring annotations; it may use Reactor types;
  - `infrastructure` holds the adapters (web, scheduler, messaging, persistence, LLM, Redis) and the Spring configuration that wires everything.
- The boundaries are enforced by **ArchUnit** tests in every service (layer direction and no framework types in domain or application). Non-blocking behavior is enforced by **BlockHound** tests.
- The external contract is in Spanish and the domain in English; mappers at the boundaries convert between them (ADR 0015).
- Time and identifiers come from ports (`ClockPort`, `IdGenerator`), so logic is testable without waiting.

## Alternatives considered

- **Layered architecture with Spring everywhere:** less code, but business rules end up coupled to the framework and to the database.
- **Clean architecture with entities, interactors and presenters:** more ceremony than this size needs.
- **Only package conventions without tests:** boundaries erode silently.

## Consequences

- Domain and use cases are tested with plain unit tests and in-memory fakes; the infrastructure is tested with Testcontainers.
- More classes (ports, adapters, mappers) than a layered design; the cost is accepted for testability and a clear migration path.
- A rule violation fails the build.
