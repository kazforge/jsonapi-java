# ADR-020: Jackson-Neutral Implementation Helpers

**Status:** Accepted
**Date:** 2026-09-09

## Context

The Jackson 2 and Jackson 3 adapters reached behavioral parity, but still carried duplicate
implementations of several small helpers. Some of those helpers describe JSON:API or core-model
bookkeeping and do not depend on a Jackson parser, mapper, type model, or serializer. Keeping those
implementations in both adapters increases maintenance cost and allows neutral behavior to drift.

Other code that appears similar is intentionally tied to a Jackson major: token traversal, source
location conversion, configured property introspection, native type models, property-scoped
serialization, binding, module registration, and runtime-specific writers. Those responsibilities
must remain isolated so the adapters preserve their native Jackson integration and do not acquire a
lowest-common-denominator abstraction.

## Decision

Keep one implementation of the neutral helper state in the existing
`jsonapi-java-jackson-api` artifact, under the unsupported namespace
`io.github.kazemek.jsonapi.jackson.internal..`. Do not create another Gradle module, Maven artifact,
public facade, parser abstraction, mapper abstraction, or traversal SPI. The classes are Java-public
only so the two adapter modules can cooperate across artifact boundaries; they are not supported
application API and must not occur in supported public signatures.

The shared ownership is deliberately limited to the following responsibilities:

| Package | Shared implementation |
|---------|-----------------------|
| `internal.wire` | Member classification, RFC 6901 escaping, neutral source-location indexing and pointer accumulation, and core-validation pointer relocation |
| `internal.mapping` | Resource-type mismatch diagnostics, identifier-meta locations/copies, and mapping property roles |
| `internal.patch` | The neutral supplied/value marker consumed by each adapter's own PATCH serializer and deserializer |
| `internal.representation` | Effective representation composition, included-resource result values, and per-invocation compound-inclusion identity/order/count/conflict bookkeeping |

`CompoundInclusionState` registers primary id/lid aliases, recognizes related occurrences under
either alias, records sparse-fieldset linkage exemptions, preserves first-discovery order,
deduplicates equivalent rendered resources, enforces the configured count limit, and reports
conflicting representations. `CompoundInclusionEngine` remains duplicated and adapter-local: it
prevalidates include paths, resolves major-specific types, reads relationships, protects traversal
cycles, and delegates only this neutral bookkeeping to a fresh state instance per collection.

The adapters continue to own Jackson parser locations. Their local `ReadLocations` converters pass a
neutral `SourceLocation` into the shared pointer accumulator. Jackson 2 and Jackson 3 native
`JavaType`, parser, mapper, introspection, property-writer, serializer/deserializer, binder, module,
and runtime types do not cross into the shared implementation packages.

## Supported API boundary

The supported Jackson API packages remain `api`, `document`, `mapping`, `patch`, `representation`,
and `diagnostic` under `io.github.kazemek.jsonapi.jackson`. The `internal` namespace is excluded
from that description. Architectural tests enforce both sides of the boundary:

- `jackson-api` remains free of Jackson-major imports and checks supported public constructors,
  methods, fields, generic signatures, and supertypes for leaked internal types.
- Jackson 2 and Jackson 3 duplicate-contract checks inventory only supported neutral packages and
  exclude both the shared `jackson.internal..` namespace and each adapter's own internal packages.

## CPD policy

The existing root Sonar CPD exceptions continue to cover deliberate major-local adaptations,
including each `CompoundInclusionEngine`. CPD entries for the seven helper implementations removed
from Jackson 2 are deleted because those responsibilities now have one shared owner. Whole-package
exclusions remain prohibited.

## Consequences

Positive consequences:

- Neutral identity, pointer, mapping-role, PATCH-marker, and representation bookkeeping has one
  implementation and one set of focused unit tests.
- Jackson-specific integration remains explicit and independently compiled in each adapter.
- The existing module and dependency direction is unchanged.
- The unsupported namespace makes implementation sharing explicit without expanding the supported
  application contract.

Tradeoffs:

- Java-public implementation classes are present in the API artifact even though applications must
  not depend on them.
- Adapter source still contains intentional duplication for native Jackson mechanics and traversal.
- Architectural rules and CPD exceptions must be maintained when the shared boundary changes.

## Alternatives rejected

- A new common implementation module was rejected because the existing API artifact already provides
  the dependency direction and no separate consumer-facing artifact is needed.
- A neutral Jackson parser/mapper or traversal abstraction was rejected because it would hide native
  major-specific behavior and create a lowest-common-denominator contract.
- A shared test runner or parity scenario registry was rejected; neutral helper semantics belong in
  API tests and observable Jackson behavior belongs in each adapter's tests.
