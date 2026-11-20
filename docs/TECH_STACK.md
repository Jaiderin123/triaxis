# Triaxis: Technology Stack

Reference document for **Triaxis**, a digital triage system with a clinical assistant (Triage service, Clinical Assistant service and Auth service).
Versions are confirmed against Maven Central and Docker Hub as they are adopted; an item still marked **(confirm)** has not been checked yet.

## 0. Naming

| Item | Convention |
|---|---|
| Project name | Triaxis |
| Repository | `triaxis` |
| Base package | `dev.triaxis.<module>` (for example `dev.triaxis.triage`); Gradle group `dev.triaxis` |
| Gradle modules | `triaxis-triage-service`, `triaxis-assistant-service`, `triaxis-auth-service`, `triaxis-common`, `triaxis-security`, `triaxis-web` |
| Docker images | `triaxis/triage-service`, `triaxis/assistant-service`, `triaxis/auth-service` |

The name must be checked on GitHub and Google before publishing, to avoid clashing with an existing health product.

## 1. Platform

| Area | Choice | Notes |
|---|---|---|
| Language | Java 21 (Temurin 21.0.10, confirmed) | LTS. Spring Boot 3.5 supports Java 17 to 25 |
| Framework | Spring Boot **3.5.16** | Last OSS release of the 3.5 line. OSS support ended in June 2026 (see ADR) |
| Reactive stack | Spring WebFlux, Project Reactor | No blocking calls on the event loop |
| Build | Gradle **8.14.7** (wrapper, confirmed), Groovy DSL, multi-module | Version catalog in `gradle/libs.versions.toml` |

## 2. Modules

| Module | Responsibility |
|---|---|
| `triaxis-triage-service` | Patient registration, classification, state flow, SLA monitoring and escalation, audit, operation report |
| `triaxis-assistant-service` | RAG over clinical protocols, real-time urgency view built from Kafka events |
| `triaxis-auth-service` | Issues JWTs (RS256) and exposes the public key (JWKS) |
| `triaxis-common` | Event contracts shared by the services |
| `triaxis-security` | Shared JWT validation configuration (resource server) |
| `triaxis-web` | Shared WebFlux plumbing: standard error responses, access log, protected metrics endpoint, tracing dependencies |

## 3. Data and messaging

| Area | Choice | Notes |
|---|---|---|
| Relational database | PostgreSQL 16 (`pgvector/pgvector:pg16` locally, `postgres:16-alpine` in tests; confirmed) | Must match a version supported by the Azure target |
| Vector search | pgvector extension (`pgvector/pgvector` image) | Custom R2DBC repository, no `PgVectorStore` |
| Database access | Spring Data R2DBC, `r2dbc-postgresql` | Schema created with init scripts (no Flyway/JDBC) |
| Messaging | Apache Kafka 3.9, KRaft mode (`apache/kafka:3.9.1`, confirmed) | Topic: `triaje.eventos` |
| Kafka client | Spring for Apache Kafka | Async `@KafkaListener` returning `Mono`, manual acknowledgment |
| Cache / read model | Redis 7.4 (`redis:7.4-alpine`, confirmed) via Spring Data Redis Reactive | Urgency view rebuilt from events |

## 4. AI

| Area | Choice | Notes |
|---|---|---|
| Framework | Spring AI **1.1.8** (latest 1.1 release, confirmed) | Spring AI 1.1 supports Boot 3.4 and 3.5. Spring AI 2.x requires Boot 4 |
| LLM and embeddings | Google Gemini through `spring-ai-starter-model-google-genai` and `spring-ai-starter-model-google-genai-embedding` (versions from the Spring AI BOM; they bring the `google-genai` 1.37.0 SDK): chat `gemini-2.5-flash`, embeddings `gemini-embedding-001` (768 dimensions), both checked against the API on 2026-11-19 | `gemini` profile; key from `GEMINI_API_KEY` only (ADR 0008) |
| Local / tests | `mock` provider, the default (deterministic bag-of-words embeddings and extractive answers) | Lets anyone run the system without an API key; tests never call a real provider |
| Retrieval | Cosine distance in pgvector with a similarity threshold | Below the threshold: fixed "no protocol available" answer, **no LLM call** |
| Reactive isolation | `EmbeddingModel` and blocking SDK calls run on `boundedElastic` | |

## 5. Resilience, observability and security

| Area | Choice |
|---|---|
| Resilience | Resilience4j **2.4.0** (`resilience4j-spring-boot3`, `resilience4j-reactor`): circuit breaker and timeout on LLM calls, explicit fallback (HTTP 503) |
| Health | Spring Boot Actuator: liveness and readiness probes |
| Metrics | Micrometer with a Prometheus endpoint protected by HTTP Basic: classification counts per level, outbox, escalations, LLM latency |
| Logging | Spring Boot structured logging, ECS JSON on the console, with the trace id; plain text in the `local` profile |
| Tracing | OpenTelemetry through Micrometer Tracing (`micrometer-tracing-bridge-otel` and the OTLP exporter, versions from the Spring Boot BOM); Jaeger locally |
| Authentication | JWT, RS256, issued by `auth-service`; validated by the other services through the public key (JWKS) |
| Secrets | Environment variables only. Nothing hardcoded or committed |

## 6. Testing

| Area | Choice |
|---|---|
| Unit and integration | JUnit 5, Reactor Test (`StepVerifier`) |
| Infrastructure | Testcontainers (PostgreSQL `postgres:16-alpine` in triage and `pgvector/pgvector:pg16` in the assistant, Kafka, the `r2dbc` module for the R2DBC service connection) |
| LLM | Mocked (never real calls in tests) |
| Coverage | JaCoCo |
| Mapping | MapStruct **1.6.3** (latest stable; 1.7 is still beta) | Generated mappers at the boundaries: DTO to command, entity to domain, domain to response |
| Architecture rules | ArchUnit **1.5.1**: enforces the hexagonal dependency rules (domain, application, infrastructure) |
| Non-blocking proof | BlockHound **1.0.18.RELEASE**: fails a test if a blocking call runs on an event-loop thread |
| Redis in tests | Testcontainers generic Redis container (`redis` image, pinned tag); no extra library |

## 7. Packaging and deployment

| Area | Choice |
|---|---|
| Containers | One Docker image per service, pinned base image tags (never `latest`) |
| Local run | `docker-compose` that starts PostgreSQL, Kafka, Redis and all services with one command |
| Orchestration | Kubernetes manifests (Deployment, Service, probes) |
| Cloud | Azure (Azure Container Apps or AKS, final choice pending) |
| Local cluster validation | kind / minikube / k3d |

## 8. Version management rule

Do **not** pin versions already managed by the Spring Boot BOM (Spring Kafka, Spring Data Redis, R2DBC driver, Reactor, Micrometer, Spring Security, Testcontainers).

Pin explicitly only:
1. The Spring Boot Gradle plugin (3.5.16).
2. The Spring AI BOM.
3. Resilience4j.
4. ArchUnit, BlockHound and MapStruct (checked: none is managed by the Spring Boot 3.5.16 BOM).
5. Docker image tags.

BlockHound on JDK 13+ needs the JVM flag `-XX:+AllowRedefinitionToAddDeleteMethods` in the test task (set in the root build; it prints a harmless deprecation warning).

## 9. Language conventions

- Code, comments, commits and documentation: **English**.
- External contract (paths, JSON fields, event names, state and level values): **Spanish**, exactly as specified (`/triage/pacientes`, `nivel`, `slaMinutos`, `REGISTRADO`, `paciente_registrado`, `triaje.eventos`).
- Internal domain model in English, mapped to the external contract at the boundary (DTOs / enums with an external value).

## 10. Decisions to record as ADRs

1. Spring Kafka instead of Reactor Kafka (Reactor Kafka discontinued in May 2025).
2. Custom R2DBC repository for vectors instead of Spring AI `PgVectorStore` (blocking JDBC).
3. Separate identity service (JWT RS256 + JWKS) and what replaces it in production (Entra ID or Keycloak).
4. Degraded mode of the assistant (circuit breaker, 503) and why Triage never depends on it.
5. Gemini for LLM and embeddings, `mock` profile, and discarded alternatives (Ollama, DeepSeek).
6. Spring Boot 3.5 / Spring AI 1.1 out of OSS support, chosen on purpose, migration path to Boot 4 / Spring AI 2.
7. Azure deployment strategy and cost control.
8. Alert channel for escalated patients.

## 11. Open items

- [x] Confirm exact versions (done as they were adopted; recheck image tags before the deployment).
- [ ] Choose Azure Container Apps or AKS, and how Kafka, PostgreSQL and Redis run there.
- [ ] Choose the alert channel and format for escalations.
- [x] Calibrate the similarity threshold with the Gemini embedding model (0.33, ASSUMPTIONS A36).
