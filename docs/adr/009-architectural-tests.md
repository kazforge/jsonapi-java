# ADR-009: Architectural Tests for Module Boundaries

**Status:** Accepted
**Date:** 2026-07-29

## Context

Gradle controls artifact dependencies, but it cannot prevent production code from referring to an
unintended type already present on a compile or test classpath. Compilation also cannot express the
package responsibility DAGs inside core and the Jackson 3 adapter.

## Decision

Use ArchUnit as a `testImplementation`-only dependency where package or type boundaries need
executable enforcement. It is the repository-wide tool for those checks; do not replace it with
source-import or classpath scanners, and never publish it as a runtime dependency.

Architecture specifications enforce allowlists for the neutral API, the Jackson 3 adapter,
shared fixtures, and query parsing, following [ADR-016](016-jackson-3-only-product-scope.md).
In particular:

- neutral production code stays free of Jackson and adapter implementation dependencies;
- the Jackson 3 adapter uses native `tools.jackson.*` types and supported lower-layer contracts;
- sibling modules do not depend on `core.internal`;
- supported neutral contracts are not redeclared by the Jackson 3 adapter, and internal
  implementation types do not leak through supported public signatures;
- shared fixtures remain passive application-shaped data and resources, apart from the neutral
  resource loader; behavioral orchestration and assertions remain adapter-local. The Jackson 3 module
  owns the shared-fixture architecture checks;
- characterization specs live directly in the Jackson 3 test source set. Following the Jackson
  3-only decision in ADR-016, shared fixtures have no assertion or orchestration exception;
  `com.kazforge.jsonapi.fixtures.contract` retains only passive application-shaped carriers.

The neutral API artifact owns supported contracts, not implementation helpers. Mapping orchestration
lives in `com.kazforge.jsonapi.jackson3.internal.mapping`, and native wire codecs live in
`com.kazforge.jsonapi.jackson3.internal.codec`. Both packages are unsupported implementation details.

Core preserves its downward responsibility DAG: aggregate validation may depend on model, internal,
and validation responsibilities; model may depend on internal and validation; internal may depend on
validation. The reverse edges are forbidden. The declared Gradle project edges themselves are the
source of truth for physical module dependencies; ArchUnit enforces production-code boundaries,
not build declarations.

The Jackson 3 adapter preserves its local DAG: the public composition root may depend on adapter
mapping contracts and both internal packages. Adapter mapping contracts do not depend on composition
or internals; internal mapping and codecs do not depend back on composition or on each other.
Codecs also do not depend on adapter mapping contracts. Same-package recursive models are allowed,
so there is no blanket cycle ban.

Every responsibility selector must match production classes so rules cannot pass vacuously. A
legitimate allowlist or protected-DAG change requires this ADR to change with the enforcing
specification.

## Consequences

- `./gradlew clean build` fails near the module that violates a protected boundary.
- Contributors treat failures as architecture violations, not tests to weaken without a decision
  change.
- Exact package inventories stay in the module-owned architecture specifications rather than this
  rationale record.
