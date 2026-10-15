# ADR-016: Jackson 3-Only Product Scope

**Status:** Accepted
**Date:** 2026-10-14
**Supersedes:** [ADR-015](015-responsibility-based-mapping-and-native-wire-codecs.md)

## Context

Maintaining multiple Jackson major adapters and a separately published internal mapping artifact adds
build, test, and publication surface before 1.0 without serving the project's Java 21+ and Jackson 3
direction. At the same time, framework- and JSON-library-neutral core, annotation, API, and query
contracts remain valuable to applications and framework integrations without requiring multiple JSON
backends.

## Decision

- Starting with the breaking `0.3.0` minor release, `jsonapi-java` supports **Jackson 3 only**
  (`tools.jackson.*`). `0.2.0` is the last published dual-major release; no `0.2.x` maintenance
  release is planned, and `0.3.0` is the next intended release under
  [ADR-014](014-unified-release-train.md).
- The unified release train publishes five consumer artifacts:
  - `com.kazforge:jsonapi-java-core`
  - `com.kazforge:jsonapi-java-annotations`
  - `com.kazforge:jsonapi-java-api`
  - `com.kazforge:jsonapi-java-query`
  - `com.kazforge:jsonapi-java-jackson3`
- `jsonapi-java-jackson2` and the separately published internal `jsonapi-java-mapping` artifact are
  retired.
- `jsonapi-java-core` remains the dependency-free document model and validation authority;
  `jsonapi-java-annotations` owns dependency-free semantic mapping roles; `jsonapi-java-api` owns
  supported neutral application, document, mapping, representation, diagnostic, and PATCH contracts
  without Jackson production imports; and `jsonapi-java-query` remains a separate optional module.
- `jsonapi-java-jackson3` owns both internal mapping orchestration (compound inclusion, resource
  reads and writes, link decoration, typed envelopes, Level-1 primary-data shape policy, PATCH
  projections, and mapping-definition invariants) and the native token-driven wire codec in distinct
  unsupported `internal` packages. Configured Jackson 3 remains the property, naming, conversion,
  and construction authority ([ADR-004](004-jackson-integration.md)).
- Do not introduce a generic multi-backend SPI, neutral JSON intermediate representation, or
  Gson/Moshi backends.
- Jackson 2 databind runtimes (`com.fasterxml.jackson.databind`) and Spring Boot 3-era Jackson 2
  runtime integrations are intentionally outside the supported compatibility matrix from `0.3.0`
  onward. This is a deliberate product-scope boundary, not a claim of zero external impact based on
  known-user count. Shared `com.fasterxml.jackson.annotation` annotations remain usable where
  supported by Jackson 3.
- `jsonapi-java-jackson3` retains its published minimum baseline (`3.1.7` on the sustainable 3.1 LTS
  line) for compilation, default tests, and published metadata, alongside the Renovate-maintained
  `jackson3-current` test leg across supported Java runtimes (Java 21 minimum and newer CI LTS).
- The separate `kazforge/jsonapi-java-spring` companion stays on its own release train, depends on
  the neutral public `jsonapi-java` contracts, and scopes its consumer examples and integration
  verification to Jackson 3.

## Consequences

- Consumers and release automation track five consumer artifacts on one release train, with no
  published internal-only mapping artifact.
- Neutral application and framework contracts (`core`, `annotations`, `api`, `query`) stay decoupled
  from Jackson internals while `jsonapi-java-jackson3` preserves a clear internal boundary between
  mapping semantics and token wire codecs.
- Discontinuing `jsonapi-java-jackson2` and `jsonapi-java-mapping` is classified as a pre-1.0
  breaking minor change and surfaced in the `0.3.0` release notes.
