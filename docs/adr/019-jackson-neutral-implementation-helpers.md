# ADR-019: Jackson-Neutral Implementation Helpers

**Status:** Accepted
**Date:** 2026-09-09

## Context

The two Jackson adapters share small pieces of JSON:API bookkeeping that do not depend on a parser,
mapper, Jackson type model, or serializer. Duplicating that neutral state invites semantic drift, but
sharing native Jackson mechanics would create a lowest-common-denominator abstraction.

## Decision

Keep neutral helper implementations in the existing `jsonapi-java-api` artifact under the
`com.kazforge.jsonapi.internal` namespace and its subpackages. These classes are Java-public
only for cross-artifact cooperation. They are unsupported application API and must not occur in
supported public signatures.

The shared boundary is limited to neutral bookkeeping for wire member classification and pointers,
mapping roles and identifier-meta copies, supplied PATCH markers, effective representation state, and
compound-inclusion identity, order, count, and conflict tracking.

Each adapter continues to own parser traversal, native source-location conversion, mapper
introspection, Jackson type models, property conversion, serializers and deserializers, binders,
module registration, writers, and inclusion traversal/type resolution. No new artifact, public
facade, neutral parser or mapper, or traversal SPI is introduced.

Supported packages remain the neutral `api`, `document`, `mapping`, `patch`, `representation`, and
`diagnostic` namespaces. Architecture tests enforce Jackson-major freedom in the neutral artifact,
prevent internal types from leaking through supported signatures, and keep adapter contracts from
being redeclared.

## Consequences

- Neutral bookkeeping has one implementation and focused tests while native Jackson integration
  remains explicit and independently compiled.
- The existing module dependency direction is unchanged.
- Unsupported Java-public classes are present in the API artifact; consumers must not depend on them.
- Intentional duplication remains for major-specific mechanics, and boundary changes require matching
  architecture-test and duplication-policy updates.
