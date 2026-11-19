# 0009. Spring Boot 3.5 and Spring AI 1.1, chosen on purpose

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

Spring Boot 4 and Spring AI 2 are the current lines, but the open source support of Spring Boot 3.5 ended in June 2026, and 3.5.16 is its last open source release. The requirements specification asks for Spring Boot 3 explicitly. Spring AI 1.1 supports Boot 3.4 and 3.5, while Spring AI 2 requires Boot 4. The project was first generated on Boot 4 and was moved back on purpose.

## Decision

- Use **Spring Boot 3.5.16** and **Spring AI 1.1.8** (the latest 1.1 release), with Java 21 and the Gradle wrapper 8.14.7.
- Pin only what the Spring Boot BOM does not manage (Spring AI BOM, Resilience4j, ArchUnit, BlockHound, MapStruct) in `gradle/libs.versions.toml`, and keep `docs/TECH_STACK.md` in sync.
- Never use Boot 4 or Spring AI 2 in this project; `CLAUDE.md` states it as a rule.

## Alternatives considered

- **Spring Boot 4 with Spring AI 2:** current and supported, but it contradicts the "Spring Boot 3" requirement and changes the starters, the Testcontainers packages and part of the security API.
- **Spring Boot 3.4:** older and also out of support, with no benefit.
- **A commercially supported 3.5 line:** keeps receiving fixes but is outside the scope and budget of this project.

## Consequences

- No new security fixes for Boot 3.5 are expected; a real deployment would migrate to Boot 4 and Spring AI 2 (new starters, Testcontainers 2 packages, adjusted security configuration).
- The migration path is helped by the hexagonal layout: framework code lives in the infrastructure layer only.
- Dependency updates inside the 3.5 line are no longer available, so the versions are frozen on purpose.
