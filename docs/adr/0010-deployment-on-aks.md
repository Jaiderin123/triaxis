# 0010. Deployment on a single-node AKS cluster, with the state in the cluster and the cost under control

- **Status:** Accepted (revised on 2026-11-20 when phase 8 was built; see the revision at the end)
- **Date:** 2026-11-20
- **Author:** Jaider Betancur Torres

## Context

The requirements specification asks for a system deployed on Azure with the APIs reachable, the Kubernetes manifests used for it (with AKS or Azure Container Apps as the target, at our choice), and a local run with one command. The system has three Spring Boot services and needs PostgreSQL with pgvector, Kafka and Redis. It is a demonstration: it must be cheap, it must be easy to start and stop, and nothing secret may reach the repository or the images. The assistant needs an LLM; the local and test runs use the deterministic `mock` provider (ADR 0008).

## Decision

- **Compute: AKS with one node.** The control plane uses the free tier. One node of a general-purpose size with about 8 GiB of memory is the starting point (three JVMs, Kafka, PostgreSQL, Redis and the cluster add-ons); the size, the region and the price are confirmed with the Azure calculator and the quota of the subscription before the cluster is created.
- **State in the cluster.** PostgreSQL (`pgvector/pgvector`), Kafka (KRaft, one broker) and Redis run as single-replica StatefulSets with persistent volumes, as in the compose file. The managed services (Azure Database for PostgreSQL, Event Hubs with the Kafka endpoint, Azure Cache for Redis) cost more and add configuration for a demonstration.
- **The same manifests everywhere.** `deploy/k8s/` holds a Kustomize base and two overlays, `kind` (local validation) and `aks`; Kustomize is built into `kubectl` (`kubectl apply -k`). The overlays change only the images, the host name, the TLS settings and the storage class.
- **Images.** One multi-stage Dockerfile per service: the build runs Gradle on a JDK 21 image, the runtime is a JRE 21 image, both with exact tags (never `latest`), and the process runs as a non-root user. The images are public on GitHub Container Registry (`ghcr.io`), tagged with the version of the release; they hold no secret and the cluster needs no pull secret.
- **Entry point.** A maintained ingress controller (Traefik is preferred; ingress-nginx was retired by the Kubernetes project in March 2026, to be checked again when the phase starts) behind the public IP of the node pool load balancer, with an Azure DNS label as the host name. The manifests use the standard `Ingress` resource with the paths `/auth`, `/.well-known/jwks.json`, `/triage` and `/asistente`, so the controller can be replaced. `/actuator` is not routed (ADR 0020, A29).
- **TLS.** cert-manager issues a Let's Encrypt certificate for the DNS label (HTTP-01 challenge through the ingress). The `kind` overlay runs without TLS on `localhost`.
- **LLM.** The deployed assistant uses Gemini (`gemini` profile) with `GEMINI_API_KEY` from a Kubernetes Secret (A16). When Gemini fails, the degraded mode answers 503 (ADR 0004); going back to `mock` is a change of the profile only.
- **Alerts.** The log channel of ADR 0011; a webhook is configured only if `ALERT_WEBHOOK_URL` is provided as a Secret.
- **Secrets.** Created in the cluster by a script from environment variables (JWT signing key, database passwords, metrics credentials, the users of auth, the Gemini key, the webhook URL). Only a template without values is versioned.
- **Replicas.** One replica of each service: the assistant must run as one (A39), and one replica of auth keeps the in-memory rate limit of the token endpoint exact (A28).
- **Infrastructure as code.** Scripts of the Azure CLI in `deploy/azure/` create and delete the resource group, the cluster and the add-ons, and apply the overlay. They are idempotent and never hold a secret.
- **Cost control.** A budget with an alert on the subscription, the cluster stopped (`az aks stop`) when there is no demonstration, and the resource group deleted when the evaluation ends. Container Insights is not enabled (the ingestion of logs is billed); logs are read with `kubectl logs`, and Prometheus, Jaeger and the export of traces are optional and off.

## Alternatives considered

- **Azure Container Apps.** No cluster to run, but it does not use Kubernetes manifests, so the deployment would have a second format, and Kafka would need Event Hubs or a container without persistent storage.
- **Managed PostgreSQL, Event Hubs and Azure Cache for Redis.** Closer to production and with backups, but each one has its own monthly cost and configuration, and Event Hubs needs the Standard tier for the Kafka endpoint.
- **Azure Container Registry.** Integrated with AKS, but the Basic tier is billed every month and the cluster needs access to it; public images on GHCR are free.
- **Plain YAML without overlays, or Helm charts.** Plain YAML duplicates the files for kind and AKS; Helm adds a tool and templates for a single deployment.
- **HTTP only.** Simpler, but the tokens would travel in clear over the internet.
- **The `mock` provider in Azure.** No key and no cost, but the deployed assistant would not show the real RAG.

## Consequences

- The manifests validated on kind are the ones applied on AKS, which is what the specification asks to deliver.
- The data lives on one persistent volume of one node: no high availability and no backups. That is acceptable for a demonstration and stated in the assumptions.
- Starting the demonstration needs the cluster to be started; the first request after a start is slower.
- The Gemini key is a Secret of the cluster; a quota or an outage of Gemini is visible as the 503 of the degraded mode.
- Prerequisites outside the repository: an Azure subscription with quota for the node size, the Azure CLI, a GitHub token that can publish packages, and the Gemini key.

## Revision of 2026-11-20 (implementation of phase 8)

Approved with the owner of the project when the phase was built, the day before the delivery:

- **Infrastructure as code: Terraform instead of Azure CLI scripts.** `deploy/azure/terraform` creates the resource group, the AKS cluster (free tier, one node), the static public IP with its DNS label in the node resource group, and an optional budget. It is declarative and a recognizable IaC for the reader; Terraform 1.9.0 was already installed. The state is local and git-ignored, because it holds the credentials of the cluster. Small scripts in `deploy/azure/` wrap it (`deploy.sh`, `stop.sh`, `start.sh`, `destroy.sh`, `push-images.sh`).
- **TLS from Traefik instead of cert-manager.** Traefik `v3.7.19` is the ingress controller, and its built-in Let's Encrypt resolver (HTTP-01 on the web entry point, certificate stored on a small persistent volume) gives the same HTTPS with one component less on a node of 8 GiB. Traefik is installed by the manifests of the base, so kind and AKS use the same controller.
- **One shared Dockerfile.** `docker/service.Dockerfile` builds any of the three services with `--build-arg SERVICE`, because the three images are built the same way from the root of the repository.
- **Size and region.** `Standard_D2as_v7` (2 vCPU, 8 GiB, AMD, not burstable) in `eastus2`. The first choice, `Standard_B2ms`, is not offered to the free trial subscription: on 2026-11-20 no B-series size was listed for it in `eastus2`, `eastus`, `centralus`, `southcentralus`, `westus2` or `westus3`. `Standard_D2as_v7` is offered in `eastus2` with quota, and its retail price that day was USD 0.0908 per hour against 0.0832 for `Standard_B2ms`; it does not depend on CPU credits. The size has no temporary disk and only an NVMe disk controller, so the OS disk is managed (no ephemeral OS disk).
- **Cost during the evaluation.** The cluster is created and checked shortly before the delivery, stopped until the repository is handed in, then left running for the whole review window so the URL always answers, and deleted at the end.
- **Deferred to after the delivery.** The database roles and the schema step of ADR 0024, and the rate limit that trusts only the forwarded address of the Ingress: the deployment uses the superuser of the PostgreSQL container and the rate limit per address of A28. The final review added the NetworkPolicy, the authorized IP ranges of the API server and the Redis password in a file (ASSUMPTIONS A47, PLAN "Improvements").
