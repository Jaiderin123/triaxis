# Triaxis

Reactive digital triage system with a clinical assistant, built with Spring Boot, Kafka and Gemini.

> **Resumen:** Triaxis es un sistema de triaje digital con un asistente clínico, compuesto por servicios de triaje, asistente y autenticación, desarrollados con Spring Boot reactivo, Kafka, PostgreSQL y Redis.

## Architecture

_TODO_

## Tech stack

See [docs/TECH_STACK.md](docs/TECH_STACK.md).

## How to run

Requirements: JDK 21 and Docker. The full one-command run (all services in containers) arrives with the deployment phase; today the infrastructure runs in containers and the services run from Gradle.

```bash
docker compose up -d postgres kafka kafka-init redis jaeger

# in separate terminals (JAVA_HOME must point to a JDK 21)
./gradlew :triaxis-auth-service:bootRun --args='--spring.profiles.active=local'
./gradlew :triaxis-triage-service:bootRun --args='--spring.profiles.active=local'
./gradlew :triaxis-assistant-service:bootRun --args='--spring.profiles.active=local'   # mock provider, no key
```

The assistant answers with the deterministic `mock` provider by default. To use Gemini, export `GEMINI_API_KEY` (never commit it) and add the `gemini` profile: `--spring.profiles.active=local,gemini`.

Local demonstration users (profile `local` only): `nurse` / `nurse-demo` and `doctor` / `doctor-demo`.

```bash
TOKEN=$(curl -s -X POST localhost:8083/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"nurse","password":"nurse-demo"}' | jq -r .access_token)

curl -s -X POST localhost:8081/triage/pacientes -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"nombreCompleto":"Ana Gomez","edad":54,"frecuenciaCardiaca":130,"presionSistolica":180,"presionDiastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"quejaPrincipal":"Dolor torácico"}'
```

### Escalation

Patients who exceed the SLA of their level are escalated automatically: every 30 seconds (`triage.escalation.interval`) a single atomic statement flags them (`escalado: true`), writes an audit entry by `system` and publishes `paciente_escalado`. The status does not change, and a patient is escalated at most once. To see it quickly, start triage with a short SLA (verified):

```bash
./gradlew :triaxis-triage-service:bootRun --args='--spring.profiles.active=local --triage.sla.minutes.CRITICAL=1 --triage.escalation.interval=PT5S'
```

### Errors

Every error of every service has the same shape (RFC 7807): `type`, `title`, `status`, `detail`, `instance`, `traceId` and `timestamp`. Validation errors add `errores`, a list of `campo` and `mensaje`.

### Observability

- **Logs:** JSON (ECS) on standard output, with `traceId` and `spanId`; the `local` profile prints plain text. One access-log line per request (never bodies or tokens).
- **Traces:** OpenTelemetry. Start a service with `TRACING_EXPORT_ENABLED=true` and open Jaeger at http://localhost:16686. Send a `traceparent` header to continue an existing trace.
- **Metrics:** `curl -u metrics:metrics-local localhost:8081/actuator/prometheus` (local profile password; elsewhere `METRICS_USER` and `METRICS_PASSWORD`, otherwise the endpoint is denied).
- **Health:** `/actuator/health/liveness` and `/actuator/health/readiness`.

### Configuration

Everything is configured through environment variables; nothing secret is in the repository. The table is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) section 10.

## API overview

| Service | Endpoint | Role |
|---|---|---|
| auth (8083) | `POST /auth/token`, `GET /.well-known/jwks.json` | public |
| triage (8081) | `POST /triage/pacientes` | `NURSE` |
| triage (8081) | `PATCH /triage/pacientes/{id}/estado` | `NURSE`, `DOCTOR` |
| triage (8081) | `GET /triage/pacientes/{id}` | `NURSE`, `DOCTOR` |
| triage (8081) | `GET /triage/reportes/operacion?desde=2026-11-19&hasta=2026-11-19`: patients, levels and waits (average and p95) per UTC hour; dates or instants with a zone, at most 31 days | `DOCTOR` |
| assistant (8082) | `POST /asistente/consulta` with `{ "pregunta": "..." }` | `DOCTOR` |
| assistant (8082) | `GET /asistente/estado-urgencias`: active patients per level, built only from the Kafka events | `NURSE`, `DOCTOR` |

The assistant answers only from the clinical protocols, with the cited `fuentes` taken from the corpus. When no protocol applies it answers `No existe un protocolo disponible para esta pregunta.` with empty `fuentes`, and when the language model is unavailable it answers 503 (ADRs 0004 and 0007).

```bash
TOKEN=$(curl -s -X POST localhost:8083/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"doctor","password":"doctor-demo"}' | jq -r .access_token)
curl -s -X POST localhost:8082/asistente/consulta -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"pregunta":"¿Cuándo se administra el primer antibiótico en sepsis?"}'
```

Security is deny-by-default: every route has an explicit role rule, so a path without one answers 401 without a token and 403 with one. Tokens are RS256 JWTs from the auth service and must carry `iss`, `aud` and `exp`.

More endpoints (alerts) are added in later phases. Assumptions and ambiguities of the specification are in [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md).

## Decisions

Architecture decisions are recorded in [docs/adr/](docs/adr/).

## Testing

_TODO_

## Deployment

_TODO_

## Author

- Name: Jaider Betancur Torres
- LinkedIn: [jaiderbt](https://www.linkedin.com/in/jaiderbt/)
- GitHub: [Jaiderin123](https://github.com/Jaiderin123)
- Portfolio: [jaiderin123.github.io/portfolio](https://jaiderin123.github.io/portfolio)
