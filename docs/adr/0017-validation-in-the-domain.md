# 0017. Business validation lives in the domain

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The registration of a patient has many validation rules (ranges of the vital signs, diastolic below systolic, name length, one decimal for temperature). They could be written as Bean Validation annotations on the request DTO, or as domain rules. Writing them in both places duplicates the rules and lets them drift apart.

## Decision

- The **domain** validates the registration data and reports every violation together, each with the name of the field (`InvalidPatientDataException` with a list of field violations). The domain uses English field names.
- The **web layer** only checks the shape of the request: malformed JSON, wrong types, decimals where integers are expected (`accept-float-as-int` is disabled and string-to-number coercion is off). It translates the domain field names to the external Spanish names in the `400` answer, which lists the fields in `errores`.
- The database has `CHECK` constraints with the same ranges as a second safety net.
- The same approach applies to the state transitions, which the domain policy checks.

## Alternatives considered

- **Bean Validation on the DTOs:** idiomatic, but the rules would live outside the domain and would need to be repeated for other entry points (a consumer or a batch).
- **Validation in the use case only:** mixes orchestration with domain rules.

## Consequences

- One source of truth for the rules, tested without Spring (every range and boundary has a unit test).
- The handler stays small; the error handler owns the translation of field names.
- A new input rule needs a domain change and a mapping of the field name for the answer.
