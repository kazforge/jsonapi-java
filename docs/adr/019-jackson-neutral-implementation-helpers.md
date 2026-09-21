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

## Partial supersession

The published-but-unsupported `jsonapi-java-mapping` artifact now owns the first extraction from
this shared boundary. Compound-inclusion path validation, traversal, identity, order, count,
conflict, and sparse-fieldset-exemption behavior moved there together with the neutral
per-invocation inclusion state and effective-representation fieldset lookup; the API artifact no
longer carries `com.kazforge.jsonapi.internal.representation`.

That extraction introduces one narrow unsupported capability interface,
`com.kazforge.jsonapi.mapping.internal.InclusionBackend`, which each backend implements over its
native type tokens, mapping definitions, property access, and selective rendering. It is
implementation detail for backend cooperation, not a public facade, consumer SPI, neutral parser or
mapper, or general traversal SPI; supported backend signatures must not expose it. The
no-new-artifact and unchanged-dependency-direction conclusions above therefore no longer apply.

A later extraction moved the neutral mapping-role enum and added backend-neutral per-property
semantic metadata to `jsonapi-java-mapping`: role, logical backend property identity, configured
backend external name, and JSON:API member name, with the adapter-independent role and name
invariants enforced on construction. The API artifact no longer carries
`com.kazforge.jsonapi.internal.mapping.PropertyRole`. Each adapter still owns its write and read
property records, native handles, and type models, and composes the shared semantic value into
them; relationship-meta metadata exists only in matched form.

A further extraction moved backend-neutral basic resource-write orchestration to
`jsonapi-java-mapping`: fieldset validation and filtering, strict versus create-request identity
rules, empty-member omission, attribute and member naming, ordinary domain-object to-one/to-many
identifier construction, relationship `data` construction, and base `ResourceObject` assembly. Each
adapter composes that writer through a second narrow unsupported capability interface,
`com.kazforge.jsonapi.mapping.internal.WriteResourceBackend`, implemented over its native type and
property tokens, mapping lookup, property access, identifier and attribute conversion, declared
cardinality and target resolution, ordinary relationship normalization, effective native types,
and per-relationship enrichment. Whole-meta conversion, decoration, and the advanced direct and
`RelationshipLinkage` forms remain adapter-owned and are applied around the shared writer rather
than folded into it.

The remaining helpers listed in the Decision (wire member classification and pointers,
identifier-meta copies, and supplied PATCH markers) stay in the API artifact under
`com.kazforge.jsonapi.internal` until a later extraction moves them.
