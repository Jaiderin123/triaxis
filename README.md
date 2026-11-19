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
docker compose up -d postgres           # PostgreSQL (also Kafka and Redis when needed)

# in two terminals (JAVA_HOME must point to a JDK 21)
./gradlew :triaxis-auth-service:bootRun --args='--spring.profiles.active=local'
./gradlew :triaxis-triage-service:bootRun --args='--spring.profiles.active=local'
```

Local demonstration users (profile `local` only): `nurse` / `nurse-demo` and `doctor` / `doctor-demo`.

```bash
TOKEN=$(curl -s -X POST localhost:8083/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"nurse","password":"nurse-demo"}' | jq -r .access_token)

curl -s -X POST localhost:8081/triage/pacientes -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"nombreCompleto":"Ana Gomez","edad":54,"frecuenciaCardiaca":130,"presionSistolica":180,"presionDiastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"quejaPrincipal":"Dolor torácico"}'
```

## API overview

| Service | Endpoint | Role |
|---|---|---|
| auth (8083) | `POST /auth/token`, `GET /.well-known/jwks.json` | public |
| triage (8081) | `POST /triage/pacientes` | `NURSE` |
| triage (8081) | `PATCH /triage/pacientes/{id}/estado` | `NURSE`, `DOCTOR` |
| triage (8081) | `GET /triage/pacientes/{id}` | `NURSE`, `DOCTOR` |

More endpoints (report, assistant) are added in later phases. Assumptions and ambiguities of the specification are in [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md).

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
