# Triaxis

Reactive digital triage system with a clinical assistant, built with Spring Boot, Kafka and Gemini.

> **Resumen:** Triaxis es un sistema de triaje digital con un asistente clínico, compuesto por servicios de triaje, asistente y autenticación, desarrollados con Spring Boot reactivo, Kafka, PostgreSQL y Redis.

## Architecture

_TODO_

## Tech stack

See [docs/TECH_STACK.md](docs/TECH_STACK.md).

## How to run

Requirement: Docker. One command builds the images and starts PostgreSQL, Kafka, Redis, Jaeger and the three services (verified):

```bash
docker compose up --build
```

The services answer on `localhost:8083` (auth), `8081` (triage) and `8082` (assistant); every port is published on `127.0.0.1` only. Stop with `docker compose down` (add `-v` to delete the data).

For development with Gradle (JDK 21), start only the infrastructure and run the services from Gradle:

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

### Alerts

When a patient is escalated, the medical team gets one alert (RF-06, ADR 0011). A listener in its own Kafka group reads `paciente_escalado` and sends the minimum: the level, the minutes waited, the SLA and the path `/triage/pacientes/{id}`, without name, vital signs or complaint. An event is alerted at most once, and a patient who is already in consultation or discharged when the event is handled is not alerted (ADR 0023). A failing channel is tried 3 times and never affects the escalation.

- **Log channel (default):** one `WARN` line of the logger `dev.triaxis.triage.alerts`.
- **Webhook channel:** a JSON POST with a `text` field (shown by Slack and Teams incoming webhooks) and the structured fields. The URL is a secret and comes only from the environment (verified with a local receiver):

```bash
ALERT_WEBHOOK_URL=http://127.0.0.1:9099/hook ./gradlew :triaxis-triage-service:bootRun \
  --args='--spring.profiles.active=local --triage.alerts.channel=webhook --triage.sla.minutes.CRITICAL=1 --triage.escalation.interval=PT5S'
```

The metric `triage_alerts_total` counts the outcomes (`sent`, `failed`, `skipped`, `duplicate`).

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

The alerts add no endpoint: they are sent from Kafka events. Assumptions and ambiguities of the specification are in [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md).

## Decisions

Architecture decisions are recorded in [docs/adr/](docs/adr/).

## Testing

_TODO_

## Deployment

Kubernetes manifests (Kustomize) are in [`deploy/k8s`](deploy/k8s): a base and the overlays `kind` (local validation) and `aks` (Azure). The decisions are in [ADR 0010](docs/adr/0010-deployment-on-aks.md).

- **One node, everything inside:** the three services, PostgreSQL with pgvector, Kafka (KRaft) and Redis, with persistent volumes; Traefik is the ingress controller, with the paths `/auth`, `/.well-known/jwks.json`, `/triage` and `/asistente`. `/actuator` is not routed.
- **Security:** the pods run without root, with a read-only file system; the Secret is created by `deploy/scripts/create-secrets.sh` from generated values and local files (never from the repository).

**Local cluster (kind, verified):**

```bash
deploy/scripts/kind-up.sh          # builds the images, creates the cluster, the Secret and the workloads
# the Ingress answers on http://localhost:8088, for example:
curl -s -X POST localhost:8088/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"nurse","password":"nurse-demo"}'
kind delete cluster --name triaxis
```

**Azure (AKS):** deployed at https://triaxis-demo.eastus2.cloudapp.azure.com (verified on 2026-11-20 with the sample flow over HTTPS and a question to Gemini; the credentials of the deployed users are handed over separately, the demo passwords are refused there). [Terraform](deploy/azure/terraform) creates the resource group, an AKS cluster with the free tier and one `Standard_D2as_v7` node (2 vCPU, 8 GiB) in `eastus2`, the static public IP with its DNS label, and an optional budget. Traefik gets a Let's Encrypt certificate for that host and redirects HTTP to HTTPS; the assistant uses Gemini with the key from the Secret.

```bash
cp deploy/azure/terraform/terraform.tfvars.example deploy/azure/terraform/terraform.tfvars   # set subscription_id
deploy/azure/deploy.sh     # terraform apply, images to GHCR, Secret, kubectl apply -k deploy/k8s/overlays/aks
deploy/azure/stop.sh       # stop the node between demonstrations; start.sh starts it again
deploy/azure/destroy.sh    # delete everything
```

The scripts read local files outside the repository: `~/.config/triaxis/ghcr.env` (a GitHub token that can publish packages), `deploy.env` (the passwords of the deployed users) and `gemini.env`. The deployed URL is added here once it is live.

## Author

- Name: Jaider Betancur Torres
- LinkedIn: [jaiderbt](https://www.linkedin.com/in/jaiderbt/)
- GitHub: [Jaiderin123](https://github.com/Jaiderin123)
- Portfolio: [jaiderin123.github.io/portfolio](https://jaiderin123.github.io/portfolio)
