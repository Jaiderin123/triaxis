# 0023. Alerts only for patients that still wait

- **Status:** Accepted
- **Date:** 2026-11-20
- **Author:** Jaider Betancur Torres
- **Supersedes:** the delivery rules of [0011](0011-alert-channel.md) only (the channel, the content and the listener stay as decided there)

## Context

ADR 0011 sends one alert per `paciente_escalado` event and records it in `alert_deliveries`, so an event is never alerted twice. The listener reads `triaje.eventos` in its own consumer group (`triage-escalation-alerts`) and commits an offset only after handling a record. The manual check of phase 7 showed a gap: when the group starts for the first time (a first deployment, or offsets lost or expired), it reads every event still retained in the topic. Escalations of hours or days before are alerted again, including patients who are already in consultation or were discharged. The same kind of late alert happens when the listener was down for a while and the patient was attended in the meantime. RF-06 says nothing about it.

## Decision

- Before sending, the use case reads the patient (it already does, for the registration time). When the patient is no longer `REGISTERED` (it is `EN_CONSULTA` or `ALTA`), nothing is sent: the outcome is `SKIPPED`, recorded in `alert_deliveries` like the others, and counted in `triage.alerts` (`outcome=skipped`).
- A patient that still waits is alerted, however old the event is: the team still has to act.
- The consumer group keeps `auto.offset.reset=earliest`, so no escalation is lost when the group starts or loses its offsets (ASSUMPTIONS A46).

## Alternatives considered

- **Keep `earliest` without the check.** No alert is lost, but a new group repeats the alerts of every retained escalation, also for patients already attended, which erodes trust in the channel.
- **`auto.offset.reset=latest`.** Only escalations after the first start of the group are alerted, but an escalation that happens just before the first assignment of the partitions is lost.
- **Skip events older than a maximum age.** It needs a new setting with an arbitrary value, and it would drop the alert of a patient that is still waiting.

## Consequences

- An alert reaches the team only while it is still useful; a replayed or late event of an attended patient leaves a `SKIPPED` record and no message.
- The check reads the status at handling time: a patient that enters consultation right after the check is still alerted (harmless).
- A patient escalated and attended while the listener was down gets no alert at all, which is the intended behavior.
- The table accepts `SKIPPED`; the schema script replaces its check constraint, so an existing database is updated at startup.
