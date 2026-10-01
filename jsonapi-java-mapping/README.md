# jsonapi-java-mapping

Backend-neutral JSON:API mapping semantics shared by the Jackson 2 and Jackson 3 runtimes. This
module is published on the unified release train so backend runtimes can consume it, but it is not
supported consumer API.

All code lives in the unsupported
[`com.kazforge.jsonapi.mapping.internal`](src/main/java/com/kazforge/jsonapi/mapping/internal/package-info.java)
package. Its package documentation maps each area to the type that owns its contract: compound
inclusion, resource writes and link decoration, flat reads, typed envelopes, the Level-1
primary-data shape policy, both PATCH projections, and mapping-definition invariants. Each area
pairs one neutral orchestrator with a narrow capability interface that the backend implements over
its configured native mechanics.

## Boundary

- Depends on `jsonapi-java-api` (which depends on `jsonapi-java-core`); never on a backend, a
  backend-native library, or a Jackson-major package.
- Backend supported public signatures must not expose this package.
- Application code must not depend on this package.

See the [architecture overview](../docs/architecture.md),
[ADR-005](../docs/adr/005-domain-mapping-and-inclusion.md),
[ADR-007](../docs/adr/007-module-boundaries.md), and
[ADR-015](../docs/adr/015-responsibility-based-mapping-and-native-wire-codecs.md).
