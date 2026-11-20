# 0011. Alerts to the medical team: a Kafka listener with a log or webhook notifier

- **Status:** Accepted; the delivery rules are partly superseded by [0023](0023-alerts-only-for-waiting-patients.md) (patients that no longer wait are skipped)
- **Date:** 2026-11-20
- **Author:** Jaider Betancur Torres

## Context

RF-06 asks the system to alert the medical team when a patient is escalated. It deliberately leaves the channel, the format and the technology open. Escalation already writes a `paciente_escalado` event to the outbox in the same transaction as the flag and the audit entry, and the relay delivers it to `triaje.eventos` (ADR 0005, ADR 0006). The alert must not slow down or break the escalation, must work without any external account, and must not send identifiable clinical data to a third party.

## Decision

- **Where it runs.** Inside the triage service. A Kafka listener in its own consumer group (`triage-escalation-alerts`) reads `triaje.eventos` and reacts only to `paciente_escalado`. It is independent from the escalation transaction and from the consumer of the assistant.
- **A port and two adapters.** The port is `AlertNotifier`.
  - `LoggingAlertNotifier` writes a structured log line. It is the default (`triage.alerts.channel: log`), so the system works with no external account.
  - `WebhookAlertNotifier` posts JSON to `ALERT_WEBHOOK_URL`, which comes from the environment only because the URL is a secret. The JSON has a `text` field that Slack and Teams incoming webhooks show, and the structured fields in Spanish. The call has connect and response timeouts.
- **Content.** The alert carries the minimum:
  - the patient id, the level, the SLA in minutes, the minutes the patient waited when escalated, and the time of the escalation;
  - the path `/triage/pacientes/{id}`, to read the rest with authentication.
  
  It carries no name, vital signs or complaint (ASSUMPTIONS A44).
- **Delivery.**
  - Up to 3 attempts in total, with growing waits between them (1 s, then 2 s). After that the alert is counted as failed, logged without patient data, and the listener goes on with the next event, so a webhook that is down never stops the queue.
  - Every handled event is recorded in a table `alert_deliveries` (event id as the key, outcome, time), and an event already recorded is not sent again. Redeliveries of Kafka, restarts and rebalances therefore do not repeat alerts (ASSUMPTIONS A45).
  - The record is acknowledged after it was handled.
- **Observability.** The metric `triage.alerts` is tagged with the outcome: `sent`, `failed` or `duplicate`. Invalid records are skipped and counted.

## Alternatives considered

- **Email through SMTP.** Familiar to the medical team, but it needs a mail server (one more container) and a new dependency, and mail is a slow channel for an urgency.
- **A Server-Sent Events stream (`GET /triage/alertas`).** A good live demonstration, but each replica only sees its own alerts unless it is fed from Kafka, and it needs an always-open client to be useful.
- **A dedicated `notification-service`.** The cleanest separation, but one more service to build, deploy and secure for a single reaction.
- **Sending the alert inside the escalation transaction.** It would make the escalation depend on the channel, which RN-03 and the outbox design avoid.
- **Retrying until the webhook answers.** No alert is lost, but one channel outage stops every later alert.

## Consequences

- The medical team gets one alert per escalated patient, a few seconds after the escalation (the relay interval plus the delivery).
- Without `ALERT_WEBHOOK_URL` the alerts are only in the logs. A real channel is configured per environment (phase 8).
- An alert that fails three times is lost for the channel but stays visible in the logs, in the metric and in `alert_deliveries`. Sending such alerts again is a possible improvement.
- A patient escalated while the listener is down is alerted when the listener comes back, because the offsets of its consumer group are committed only after handling.
