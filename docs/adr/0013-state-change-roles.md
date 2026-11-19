# 0013. Who may change a patient's state

- **Status:** Accepted
- **Date:** 2026-11-18
- **Author:** Jaider Betancur Torres

## Context

The requirements specification asks for authentication on the endpoints but does not define roles. `PATCH /triage/pacientes/{id}/estado` moves a patient through `REGISTRADO`, `EN_CONSULTA` and `ALTA`. In an emergency room the nurse registers patients, but it is the doctor who starts the consultation and who discharges the patient. The first draft of the architecture allowed only `NURSE` to change state.

## Decision

Both `NURSE` and `DOCTOR` may change a patient's state. Registration stays with `NURSE`. Reports and the clinical assistant stay with `DOCTOR`. Reading a patient or the urgency view is open to both roles. The rule lives in the `SecurityWebFilterChain`, not in the domain.

## Alternatives considered

- **Only `NURSE` changes state:** simple, but blocks the real flow where the doctor starts the consultation.
- **A transition-specific rule (nurse for discharge, doctor for consultation):** closer to some hospital policies, but the specification does not define it and it adds rules that cannot be validated.
- **A single role for everything:** the least code, but it removes any demonstration of authorization.

## Consequences

- A doctor token can call `PATCH`; tests cover 403 for a missing token role and 200 for both roles.
- The audit trail records the patient and the transition, not who performed it. Recording the user id from the JWT `sub` is a possible follow-up.
- Tightening the policy later is a one-line change in the filter chain.
