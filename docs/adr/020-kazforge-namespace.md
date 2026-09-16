# ADR-020: KazForge Namespace and Maven Group

**Status:** Accepted
**Date:** 2026-09-12

## Context

The project needs one controlled, permanent Maven group and Java package base before its first stable
release.

## Decision

- The Maven group is `com.kazforge`.
- The Java base package is `com.kazforge.jsonapi`.
- Artifact IDs remain `jsonapi-java-*`.
- The pre-1.0 adoption is intentionally source- and binary-incompatible; no compatibility aliases,
  relocation artifacts, or parallel coordinates are provided.
- Repository identity, module membership, publication automation, release policy, and external
  service identifiers are outside this decision.

## Consequences

- Consumers use `com.kazforge` dependency coordinates and `com.kazforge.jsonapi.*` imports.
- Build configuration, source paths, documentation, and package-sensitive architecture rules use the
  same permanent namespace.
- Stable artifact names are retained while the group and Java package base have one authority.
