# 0024. A database role per service, and the schema applied outside the startup

- **Status:** Accepted
- **Date:** 2026-11-20
- **Author:** Jaider Betancur Torres

## Context

Today every service connects with the superuser of the PostgreSQL container and runs its idempotent schema script at startup (`spring.sql.init.mode: always`). It is simple for local work, but a service can then change or drop any table, including the append-only audit, whose protection relies on a trigger that the same superuser can remove (ADR 0014). The review of 2026-11-19 moved the fix to the deployment phase, because it changes how the containers and the manifests start.

## Decision

- **Roles.** Each database has an owner role, used only to apply the schema, and a runtime role per service with the least privileges:
  - triage: `SELECT`, `INSERT` and `UPDATE` on its tables, but only `INSERT` and `SELECT` on `patient_audit`;
  - assistant: what its repositories use on the corpus and the vectors.
  The superuser is used only by the image of PostgreSQL to create the databases, the roles and the `vector` extension on the first start.
- **Schema outside the startup.** The same idempotent scripts of the services are applied by a separate step with the owner role: a Kubernetes Job in the manifests and a one-shot service in the compose file. The services start with `spring.sql.init.mode: never`, after the schema step: in the compose file through `depends_on` with `service_completed_successfully`, and in the cluster the deploy script waits for the Job (`kubectl wait --for=condition=complete`) before it applies the Deployments. The tests keep `always` (Testcontainers starts an empty database).
- **Passwords.** From environment variables in the compose file and from Secrets in the cluster, never in the repository.

## Alternatives considered

- **Keep the superuser and the startup scripts.** No change, but any service can alter the audit and any other database.
- **A migration tool (Flyway or Liquibase).** Versioned migrations, but they need a JDBC driver next to R2DBC, which the stack excludes (CLAUDE.md, TECH_STACK).
- **An init container in every pod.** The schema would be applied before each service starts, but every replica would run it, and the owner credentials would reach the pods of the services.

## Consequences

- A service cannot change the structure of its database or remove the audit trigger, and triage cannot update or delete audit rows even through SQL.
- The schema is applied once per deployment, before the services start; a change of the schema is a new run of the same idempotent scripts.
- The tests are unchanged; the compose file and the manifests gain one step each.
