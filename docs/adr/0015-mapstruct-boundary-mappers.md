# 0015. MapStruct for boundary mappers

- **Status:** Accepted
- **Date:** 2026-11-18
- **Author:** Jaider Betancur Torres

## Context

The architecture keeps the Spanish external contract and the storage model out of the domain, so each service needs mappers at its boundaries: request DTO to command, persistence entity to domain object, and domain object to response DTO. Hand-written mappers are repetitive and easy to leave out of sync when a field is added. The auth service has no such mappers yet (its single response is built by hand), so the decision applies from the triage service onwards.

## Decision

- Boundary mappers are generated with **MapStruct 1.6.3** (the latest stable release; 1.7 is still a beta), with the Spring component model.
- Mappers only copy and rename data. Validation and business decisions stay in the domain: the request mapper produces a plain registration object and the domain validates it, so field names and rules are not duplicated.
- External enum values (`CRITICO`, `REGISTRADO`) are converted in the mappers; the domain enums have no external value.
- The version is pinned in `gradle/libs.versions.toml`, because the Spring Boot BOM does not manage it.

## Alternatives considered

- **Hand-written mappers:** no dependency, but more code and more risk of omissions.
- **ModelMapper or Dozer:** reflection based, errors appear at runtime and not at compile time.
- **Mapping inside the DTO or entity classes:** couples the boundary types to the domain.

## Consequences

- Mapping mistakes (missing or renamed fields) fail at compile time.
- A new annotation processor in the build.
- Mappers with custom rules (enums, field renaming) still need a few explicit methods and tests.
