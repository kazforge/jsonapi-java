# ADR-007: Optional Adapter Modules

**Status:** Accepted
**Date:** 2026-07-26

The artifact ownership below describes the accepted pre-extraction layout.
[ADR-022](022-responsibility-based-mapping-and-native-wire-codecs.md) supersedes the neutral
contract artifact name and adds the internal mapping artifact and backend dependency edge. Core,
annotations, query, framework isolation, and separately compiled native-major adapters remain
valid decisions.

## Context

Document-model consumers should not acquire Jackson or Spring transitively, domain classes should
not depend on web frameworks, and query parsing is useful outside transport integrations. Jackson 2
and Jackson 3 also need independent native integration rather than runtime-major detection.

## Decision

Keep these responsibility boundaries:

- `jsonapi-java-core` owns the dependency-free document model and validation.
- `jsonapi-java-annotations` owns dependency-free domain-mapping roles.
- `jsonapi-java-jackson-api` owns Jackson-major-neutral application, document, mapping,
  representation, diagnostic, and PATCH contracts. It contains no Jackson mechanics or runtime
  dependency; [ADR-018](018-level-one-application-api-contract.md) owns its Level-1 operation seam.
- `jsonapi-java-jackson3` and `jsonapi-java-jackson2` are separately compiled native-major
  implementations of those contracts. They do not share a runtime artifact or detect a major at
  runtime.
- `jsonapi-java-query` owns optional framework- and Jackson-neutral query parsing.
- Framework integrations are separate optional modules that depend on lower-layer public contracts;
  lower layers never depend on a framework.

`settings.gradle.kts` is the authority for current build membership. The root module registry
distinguishes current modules from planned integrations. Public coordinates use the namespace chosen
by [ADR-020](020-kazforge-namespace.md).

## Consequences

- Core and annotations remain usable without third-party runtime dependencies.
- Consumers select only the adapters they need, and neutral callers can avoid choosing a Jackson
  major in their own contracts.
- Jackson-major implementations can evolve natively while preserving semantic contract parity.
- More artifacts and independently enforced boundaries are accepted in exchange for dependency and
  responsibility isolation.
