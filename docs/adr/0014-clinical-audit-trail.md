# 0014. Clinical audit trail

- **Status:** Accepted
- **Date:** 2026-11-18
- **Author:** Jaider Betancur Torres

## Context

The requirements specification (RN-04) asks that every change of state or flag (registration, entry to consultation, escalation, discharge) is recorded with timestamp, patient id, previous state, new state and event type, and that the audit cannot be deleted through the API. It does not say who must be recorded, but in a clinical setting an audit without the author of the change has limited value, and two roles can change a patient's state (ADR 0013).

## Decision

- Audit entries live in the table `patient_audit`, one row per change, with `patient_id`, `actor`, `event_type`, `previous_status`, `new_status`, `escalated` and `occurred_at`.
- `actor` is the `sub` claim of the JWT of the caller, or `system` when the change is made by the escalation scheduler. The use cases receive the actor as an explicit argument, so the domain and application layers do not read the security context.
- The audit row is written in the **same transaction** as the change (and, from phase 3, the outbox row). A change cannot exist without its audit entry, nor the other way round.
- The table is append-only: a database trigger rejects `UPDATE`, `DELETE` and `TRUNCATE`, and the API has no delete endpoint. In production the service role would only be granted `INSERT` and `SELECT` on it.
- The time comes from the `Clock` port in UTC.
- Internal values are English enums (`REGISTERED`, `CONSULTATION_STARTED`, `DISCHARGED`, `ESCALATED`); the Spanish event names (`paciente_registrado`, and so on) belong only to the Kafka contract.
- The audit table is separate from the outbox table: the outbox is a delivery queue whose rows are marked as published, while the audit is the permanent record.
- A read endpoint for the audit is **out of scope** for now and tracked as an improvement in the plan.

## Alternatives considered

- **Audit without the actor:** meets the letter of the specification, but changes cannot be attributed.
- **Derive the audit from Kafka events:** couples the legal record to messaging and loses entries if publication fails.
- **Application-level protection only (no trigger):** a bug or a direct SQL statement could still alter history.
- **Event sourcing for patients:** a stronger audit by construction, but much more machinery than the scope needs.

## Consequences

- Every use case that changes a patient needs the actor as input, and the handlers extract it from the authenticated principal.
- The trigger makes tests and local clean-ups harder: test databases are recreated instead of truncated.
- Reading the audit requires direct database access until the read endpoint is built.
