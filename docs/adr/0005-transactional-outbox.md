# 0005. Transactional outbox for event publication

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

Every state change of a patient must be published to Kafka (`paciente_registrado`, `paciente_en_consulta`, `paciente_alta`, `paciente_escalado`), and the assistant builds the urgency view from those events only. Writing to PostgreSQL and to Kafka are two separate operations: publishing right after the commit can lose the event if the process dies in between, and publishing before the commit can announce a change that is later rolled back.

## Decision

- Use a **transactional outbox**. The use cases register the event through the `EventPublisher` port, and the adapter inserts it into the table `outbox_events` in the same transaction as the patient row and the audit entry. The event exists if and only if the change was committed.
- A **relay** inside the triage service delivers pending rows to Kafka:
  - each cycle runs in one transaction and first takes a PostgreSQL advisory lock, so only one relay works at a time (this keeps the order of the events of a patient, also with several replicas);
  - it reads the oldest pending rows with `FOR UPDATE SKIP LOCKED`, sends them in order and marks each one as published after Kafka acknowledged it;
  - a failed send stops the cycle and the event stays pending, so it is retried in the next one;
  - the producer uses `acks=all` and idempotence, with the patient id as the message key.
- Delivery is **at least once**: if the process dies after the send but before the commit, the event is sent again. Consumers are idempotent and use the event id.
- The trace of the request is stored with the event (`correlation_id` holds the trace id and `traceparent` the W3C trace context) and sent as the Kafka headers `correlationId` and `traceparent`, with the event name in `eventType`, so a consumer can continue the trace (ADR 0019).
- The metric `triage.outbox.pending` shows how many events wait, and `triage.outbox.published` and `triage.outbox.failures` count the results.

## Alternatives considered

- **Publish after the commit with retries:** simpler, but an event can be lost when the process dies between the commit and the send. It was kept only as a documented fallback if time ran short.
- **Publish inside the transaction:** announces changes that may be rolled back.
- **Change data capture (for example Debezium):** removes the relay code but adds another component to run and deploy.
- **Kafka transactions across both systems:** not possible, PostgreSQL is not part of the Kafka transaction.

## Consequences

- No event is lost when Kafka is down: events accumulate in the outbox and are delivered when it comes back, in order.
- The published rows stay in the table; a clean-up of old rows is a future improvement.
- Latency is the relay interval (one second by default) plus the send time.
- One extra table, one extra write per change, and a loop that has to be monitored (the pending gauge).
