# ADR-007: Optional Adapter Modules

**Status:** Accepted  
**Date:** 2026-07-26  
**Amended:** 2026-07-30 (registers `jsonapi-java-jackson3` write surface); 2026-08-10 (registers `jsonapi-java-jackson-common`); 2026-08-16 (presence-aware PATCH command contracts move to jackson-common); 2026-08-31 (renames `jsonapi-java-jackson-common` to `jsonapi-java-jackson-api` and reorganizes public contracts into concept packages); 2026-09-04 (evolves `jsonapi-java-jackson-api` from neutral values to values plus the narrow Level-1 operation contract owned by ADR-019)

## Context

Document consumers should not acquire Jackson or Spring transitively, and domain classes should not depend on web-framework types. Query parsing is useful outside Spring but is not part of a JSON:API document.

## Decision

Use these module boundaries:

- `jsonapi-java-core`: dependency-free document model and validation;
- `jsonapi-java-annotations`: dependency-free domain-mapping annotations;
- `jsonapi-java-jackson-api`: public Jackson-major-neutral API surface for codec and
  domain-mapping policy, diagnostics, contexts, domain envelope values, presence-aware
  update-command values, and the Level-1 application operation contract
   (`com.kazforge.jsonapi.jackson.api`: root `JsonApi` plus resources,
  relationships, documents, and patches facets with their option/result values), with no
  runtime dependency on either Jackson major and no Jackson-mechanics abstraction;
  [ADR-019](019-level-one-application-api-contract.md) owns that operation contract;
- `jsonapi-java-jackson3`: Jackson 3 document codec (writer, reads, and mapping),
  flat DTO mapping, typed envelopes, and PATCH reader entry points that produce the
  API presence-aware update commands; depends on `jsonapi-java-jackson-api` for neutral
  contracts;
- `jsonapi-java-jackson2`: separately compiled Jackson 2 artifact with parity contracts; consumes
  the same API contracts;
- `jsonapi-java-query`: optional query-parameter parser;
- `jsonapi-java-spring-webmvc`: optional Spring Boot WebMVC integration;
- `jsonapi-java-spring-webflux`: separately evaluated future integration.

Spring modules depend on the lower layers they adapt. The first WebMVC adapter targets Jackson 3;
Jackson 2 remains usable without Spring integration. Lower layers never depend on Spring.

Package and Maven coordinates use the permanent namespace in ADR-021 (`com.kazforge` / `com.kazforge.jsonapi`).

## Consequences

- Core remains usable with no third-party runtime dependency.
- Consumers select only the adapters they need.
- Jackson 2 and Jackson 3 public APIs never share one runtime artifact or use runtime major
  detection; both majors may share the neutral `jsonapi-java-jackson-api` contract artifact
  without combining any major's implementation.
- WebMVC can stabilize without coupling its release to WebFlux.
- More artifacts and Gradle subprojects must be maintained.
- Build complexity is accepted in exchange for dependency and responsibility boundaries.
