# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) of Triaxis.

## Process

1. Copy [0000-template.md](0000-template.md) to `NNNN-short-title.md`, using the next free number.
2. Fill in every section and set the status to `Proposed`.
3. Once agreed, change the status to `Accepted`. Never delete an ADR; mark it `Superseded by NNNN` instead.

Ambiguities in the requirements specification that change behavior are recorded as ADRs, together with the smaller entries in [../ASSUMPTIONS.md](../ASSUMPTIONS.md).

## Index

| # | Title | Status |
|---|---|---|
| 0000 | [Template](0000-template.md) | n/a |
| 0003 | [Separate identity service with RS256 and JWKS](0003-identity-service-rs256-jwks.md) | Accepted |
| 0013 | [Who may change a patient's state](0013-state-change-roles.md) | Accepted |
