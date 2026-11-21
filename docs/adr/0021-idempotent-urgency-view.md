# 0021. Idempotent urgency view that tolerates missing, repeated and late events

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

RF-04 B asks for `GET /asistente/estado-urgencias`: the number of active patients (every patient not in `ALTA`) per triage level. The assistant must build this view only by consuming `triaje.eventos`, never by calling the triage API, and keep it in Redis or another NoSQL store. The requirement does not say how the view must behave when the event stream is imperfect, but ours is imperfect by design:

- The triage outbox delivers **at least once** (ADR 0005). A relay that dies after sending and before marking the row sends the event again.
- The consumer acknowledges an event only after Redis was updated. A crash between the update and the acknowledgment replays the event.
- The view may start reading when patients are already in progress: a new Redis instance, a view rebuilt after data loss, or events older than the topic retention that are no longer there.
- A rebuild replays the whole topic.

The first rule (ASSUMPTIONS A19) counted only `paciente_registrado` (plus one) and `paciente_alta` (minus one) and ignored a discharge of an unknown patient. With that rule:

1. A patient whose registration event the view never saw is **never counted**, although they are still waiting or in consultation (the count is too low).
2. A registration event repeated after the discharge was applied, once the view no longer remembers the patient, **counts the patient again** (the count is too high).

## Decision

Each patient has a small state in Redis and a Lua script applies every event to it atomically:

| Event | Patient unknown | Patient active | Patient discharged |
|---|---|---|---|
| `paciente_registrado`, `paciente_en_consulta`, `paciente_escalado` | Store as active with the level of the event, add 1 to that level | No change | No change |
| `paciente_alta` | Store as discharged, no count change | Mark discharged, subtract 1 from the stored level | No change |

- Every event carries the level (section 9 of `ARCHITECTURE.md`), so any event can register an unknown patient. The level never changes after registration.
- "Discharged" is a final state: no later event brings the patient back. This matches the triage state machine, where `ALTA` has no outgoing transition.
- The discharged marker expires after `assistant.urgency.discharged-ttl` (30 days by default), which must be longer than the topic retention (7 days), so a replay can never meet a registration whose discharge marker is already gone.
- The script also keeps the time of the newest applied event (`actualizadoEn` in the response).
- Offsets are committed only after the script succeeds. When the view metadata key is missing at startup (new or lost Redis), the consumer reads the topic again from the beginning, and the same rules rebuild the counts. (The review of 2026-11-19, ADR 0022, moved the marker to its own key, `urgency:initialized`, written only by the reset before a replay.)

## Alternatives considered

- **The first rule (A19): only the registration adds.** Simpler script, but wrong counts in the two cases above, which are normal with at-least-once delivery and rebuilds.
- **A set of processed event ids (deduplication only).** Removes exact duplicates but does not count a patient whose registration was never seen. It also needs one key per event instead of one per patient.
- **Recomputing from the triage database or API.** Accurate, but forbidden: the assistant must not depend on triage except through the topic.
- **Counting with Kafka Streams (a KTable per patient).** Correct and replayable, but adds a new library, a state store and more operations work for one counter.

## Consequences

- The counts are right with duplicates, replays, a late start and a rebuild. The tests cover each case, including a discharge that arrives before the registration of the same patient.
- One key per patient (active or discharged) plus one hash of counters, about 100 bytes per patient. Discharged keys expire after their TTL.
- The script has about 15 lines and is covered by tests against a real Redis.
- If reclassification is ever added (a level changing after registration), the script must move the patient between levels, and a new event type is needed (ASSUMPTIONS A33).
