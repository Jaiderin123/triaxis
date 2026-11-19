# 0003. Separate identity service with RS256 and JWKS

- **Status:** Accepted
- **Date:** 2026-11-18
- **Author:** Jaider Betancur Torres

## Context

The requirements specification asks for basic authentication on the endpoints (JWT or API key) and for no hardcoded credentials. Two services (triage and assistant) must authorize requests, and neither should hold a signing secret. Both are reactive and must keep working when the identity service is down for a while.

## Decision

- A separate `triaxis-auth-service` issues JWTs signed with **RS256** and publishes the public key at `GET /.well-known/jwks.json`.
- Tokens last 15 minutes and carry `sub`, `roles`, `iss`, `iat`, `exp` and `jti`; the header carries a `kid`.
- `triage-service` and `assistant-service` are OAuth2 resource servers configured by the shared module `triaxis-security`. They validate signature, issuer and expiration using the public key, which is fetched lazily from the JWKS endpoint, so they do not need `auth-service` to start.
- The signing key is read from `JWT_PRIVATE_KEY`. A key pair is generated only when `auth.jwt.generate-key-if-missing=true` (local profile); otherwise the service refuses to start.
- `auth-service` does **not** depend on `triaxis-security`: it creates tokens and does not validate them.
- Users come from configuration with BCrypt hashes. The only credentials in the repository are demonstration users for the local profile.

## Alternatives considered

- **HS256 with a shared secret:** simpler, but every service would hold the signing secret, so any of them could forge tokens.
- **API key per client:** no expiration or roles, and revocation is manual.
- **Embedding token issuing in triage:** couples identity to a business service and breaks the independence of the assistant.
- **A managed identity provider (Microsoft Entra ID, Keycloak) from the start:** the right production answer, but it adds infrastructure and cost; the resource servers do not change when the issuer is replaced.

## Consequences

- Validation is local to each service, with no call to `auth-service` per request.
- Key rotation is possible through the `kid` and the JWKS endpoint; it needs a procedure that is not part of this scope.
- Tokens cannot be revoked before they expire; the short lifetime limits the exposure.
- A production deployment would delegate identity to a managed provider without changing the two resource servers.
