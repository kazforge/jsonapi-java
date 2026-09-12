# ADR-021: KazForge Namespace and Maven Group

**Status:** Accepted
**Date:** 2026-09-12
**Supersedes:** [ADR-008](008-public-namespace.md)

## Context

ADR-008 selected `io.github.kazemek` as the Maven group and
`io.github.kazemek.jsonapi` as the Java base package. That decision and its verification evidence
remain historical; they no longer define the project's public identity.

The project needs one permanent Maven group and Java package base before its first stable release.
The project owner confirmed control of `com.kazforge` in the Maven Central Portal and confirmed that
the intended publisher is authorized to publish artifacts under that namespace.

## Decision

- The permanent Maven group is `com.kazforge`.
- The permanent Java base package is `com.kazforge.jsonapi`.
- Artifact IDs remain `jsonapi-java-*`.
- This pre-1.0 rename is an intentional source- and binary-incompatible change.
- Do not provide compatibility aliases, relocation artifacts, or parallel coordinates for the former
  group or package base.
- Repository and GitHub identity, module membership, publication automation, release policy, and
  Sonar service identities are outside this decision and remain unchanged.

## Consequences

- Consumers migrate imports and dependency coordinates to the new namespace and group.
- Published artifacts retain their existing artifact IDs, so only the group portion of the Maven
  coordinate changes.
- Documentation, build configuration, source paths, and package-sensitive architectural rules use
  the permanent namespace.
- ADR-008 remains available solely as the historical record of the former namespace decision and
  evidence.
