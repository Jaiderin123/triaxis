# 0006. Atomic escalation with a conditional update and `SKIP LOCKED`

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

RF-03 says that a patient who exceeds the waiting-time SLA of their level must be escalated automatically. RN-03 adds that a patient is escalated **at most once**, and never after entering consultation (`EN_CONSULTA`) or being discharged (`ALTA`). The escalation runs on a timer, while nurses and doctors keep changing statuses through `PATCH /triage/pacientes/{id}/estado`. The service may also run as several replicas. A "read the overdue patients, then update each one" loop leaves a window in which a concurrent status change, or a second replica, produces a duplicate or an escalation after consultation.

## Decision

- Escalate in **one atomic statement** per batch. A CTE selects the oldest `REGISTERED` patients that were never escalated and whose `registered_at + sla_minutes` is strictly earlier than `now` (ASSUMPTIONS A6), with `ORDER BY registered_at, id`, a `LIMIT` and `FOR UPDATE SKIP LOCKED`. The outer `UPDATE ... RETURNING` sets `escalated = TRUE` and `escalated_at = now`, and checks the status and the flag again on the locked rows.
- In the **same unit of work**, write one audit entry per escalated patient (type `ESCALATED`, actor `system`, the same status before and after, ADR 0014) and one `paciente_escalado` event in the outbox (ADR 0005). Either the flag, the audit entry and the event are all committed, or none of them is.
- Escalation never changes the status or `status_changed_at`, so an escalated patient can still enter consultation (ASSUMPTIONS A9).
- The `EscalationScheduler` input adapter runs the use case every `triage.escalation.interval` (30 seconds by default). Cycles never overlap. A full batch (`triage.escalation.batch-size`, 100 by default) starts another batch at once, and a failed cycle is logged and retried on the next tick. Time comes from `ClockPort`, so tests use a fixed clock and back-dated patients instead of waiting.
- The counter `triage.escalations`, tagged by level, counts escalated patients after the commit.

How this meets RN-03:

| Situation | Outcome |
|---|---|
| The scheduler runs again | The patient is already flagged, so the `WHERE` excludes it: no second event or audit row |
| A `PATCH` commits first | The patient is no longer `REGISTERED` and is never selected |
| The escalation commits first | The waiting `PATCH` then proceeds, because an escalated patient may enter consultation. The audit shows `ESCALATED` before `CONSULTATION_STARTED` |
| The row is locked by a `PATCH` in flight | `SKIP LOCKED` skips it. A later cycle selects it only if it is still waiting |
| Two replicas run at the same time | Each one locks different rows. Every patient is escalated once |

## Alternatives considered

- **Read, then update each patient from the application:** needs a conditional update per row anyway and opens the race window between the read and the write. It is also N round trips per cycle.
- **Optimistic locking with a version column:** detects the conflict but turns it into retries. The conditional `WHERE` already gives the same guarantee without a new column.
- **A distributed lock (advisory lock or a leader replica):** serializes the scheduler but does not protect against a concurrent `PATCH`, and a lost leader stops escalation. `SKIP LOCKED` lets every replica work safely.
- **A `pg_cron` job or a database trigger:** keeps the logic out of the service and its tests, needs an extension, and makes writing the outbox event and the metric harder.
- **A separate escalation service:** more deployment cost for one statement.

## Consequences

- RN-03 is enforced by the database, and the tests prove it: a second run is a no-op, `EN_CONSULTA`, `ALTA` and escalated patients are never selected, the limit is strict, two concurrent runs escalate each patient once, and 25 rounds of a concurrent `PATCH` keep the audit order.
- An escalation is late by at most one interval plus the batch time. A stricter deadline means a shorter interval.
- The index `idx_patients_sla (status, escalated, registered_at)` supports the selection. The SLA expression `registered_at + make_interval(mins => sla_minutes)` cannot use the index for the time condition by itself, which is fine for the expected number of waiting patients. A stored `sla_deadline` column would make it indexable if volume grows.
- Escalations started by the scheduler carry no request trace, so their outbox rows have no `correlation_id` (ASSUMPTIONS A31).
- Alerts to the medical team (RF-06, phase 7) react to the `paciente_escalado` event and never affect this transaction.
