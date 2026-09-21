# jsonapi-java-mapping

Internal cross-artifact mapping implementation namespace for backend runtimes. This module is
published on the unified release train so backend runtime implementations can consume it, but it
is not supported consumer API.

The [`com.kazforge.jsonapi.mapping.internal`](src/main/java/com/kazforge/jsonapi/mapping/internal/package-info.java)
package owns backend-neutral compound-inclusion semantics for mapped resources: include-path
validation, breadth-first traversal of intermediate and nested resources, identity aliasing,
first-encounter order, included deduplication and conflict checks, traversal limits, and
sparse-fieldset linkage exemptions. It also owns the neutral per-invocation inclusion state and
effective-representation fieldset lookup used by selective rendering and traversal alike.

Native type resolution, property lookup and access, relationship-container handling, identifier
conversion, and selective resource rendering stay in each backend behind the
`InclusionBackend` capability bridge. This module imports no Jackson-major or concrete-adapter
package.

## Boundary

- Depends on `jsonapi-java-api` (which depends on `jsonapi-java-core`); never on a backend or a
  backend-native library.
- Backend supported public signatures must not expose this package.
- Application code must not depend on this package.

See the [architecture overview](../docs/architecture.md),
[ADR-005](../docs/adr/005-domain-mapping-and-inclusion.md),
[ADR-007](../docs/adr/007-module-boundaries.md), and
[ADR-019](../docs/adr/019-jackson-neutral-implementation-helpers.md).
