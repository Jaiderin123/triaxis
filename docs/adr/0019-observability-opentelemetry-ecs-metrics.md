# 0019. Observability: OpenTelemetry traces, ECS logs, metrics and health

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The requirements specification asks for health and readiness probes, structured logs with correlation and Micrometer metrics, and lists distributed tracing with OpenTelemetry as a bonus. A custom correlation id was built for the outbox, but a trace id solves the same problem and also works across services and Kafka, so keeping both would mean building and then replacing the custom one.

## Decision

- **Traces:** OpenTelemetry through Micrometer Tracing (`micrometer-tracing-bridge-otel` and the OTLP exporter, both managed by the Spring Boot BOM). Context propagation of Reactor is enabled (`spring.reactor.context-propagation=auto`), so the trace follows the reactive chain. Export is off unless `TRACING_EXPORT_ENABLED=true` and an OTLP endpoint (`OTEL_EXPORTER_OTLP_ENDPOINT`) are set; the local compose file includes Jaeger. The sampling probability is configurable.
- **Correlation:** the trace id is the correlation id. It appears in every log line, in the error bodies (`traceId`), in the outbox row and in the Kafka headers `correlationId` (trace id) and `traceparent` (W3C), so a consumer can continue the trace. The custom correlation filter was removed.
- **Logs:** Spring Boot structured logging in ECS (Elastic Common Schema) JSON on the console, with the service name. The `local` profile switches to plain text. Logs are written to standard output and collected by the platform (Container Insights in Azure); they are never stored in the application database. The clinical audit (ADR 0014) is a different thing and lives in PostgreSQL.
- **Access log:** one structured line per request with method, path, status, duration and user. Never bodies, headers, tokens or query strings. Health probes are skipped.
- **Metrics:** Micrometer with a Prometheus endpoint and a common tag `application`. Business metrics: `triage.classification` by level, `triage.outbox.*`, and, added later, `triage.escalations` (phase 4) and the assistant metrics (phase 5). The endpoint is protected (ADR 0020).
- **Health:** liveness uses the liveness state only. Readiness of the triage service includes the database but not Kafka: the outbox absorbs a Kafka outage, so a broker problem must not take the pod out of service. Shutdown is graceful.

## Alternatives considered

- **Custom correlation id and MDC bridge:** simpler to start, but it would be replaced by traces later and does not cross services by itself.
- **Brave or Zipkin tracing:** equivalent, but OpenTelemetry is the standard and the one the bonus names.
- **Logstash or GELF JSON formats:** also built into Spring Boot; ECS was chosen for its wide tool support.
- **Storing logs in the database:** costly, mixes concerns and duplicates what the platform already collects.

## Consequences

- With no collector configured nothing is exported, but trace ids still appear in logs and answers.
- The trace id field is written as `traceId` at the top level of the JSON line, not as the nested ECS field `trace.id`.
- Kafka consumers must read the `traceparent` header to continue the trace (done in the assistant phase).
- Azure needs Container Insights (logs), a Prometheus scrape (metrics) and an OTLP endpoint (traces), to be decided in the deployment ADR.
