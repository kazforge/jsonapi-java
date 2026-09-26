# ADR-005: Separate Linkage from Inclusion

**Status:** Accepted
**Date:** 2026-07-26

## Context

Relationship linkage and top-level compound inclusion are separate JSON:API decisions. Traversing
every mapped relationship would risk disclosure, lazy loading, cycles, and unbounded output. Sparse
fieldsets can also remove linkage needed by full-linkage validation, so request selection, application
policy, and mapping provenance need distinct owners.

## Decision

`@JsonApiRelationship` identifies a semantic role only; configured Jackson owns property discovery,
wire names, and conversion. Mapping a relationship produces linkage. A related resource enters
`included` only when an operation-scoped `RepresentationSelection` requests its path and the
application-scoped `RepresentationPolicy` permits it.

Selected ordinary mapped relationships always emit `data`: null or empty optional yields explicit
null to-one linkage; an empty to-many value yields an empty collection. A flat read of a relationship
without `data` leaves its linkage property unbound; explicit null remains distinct and is invalid for
a to-many target. Relationship meta can bind independently of linkage. Links-only and meta-only
relationships remain representable through document APIs, not ordinary DTO or Level-1 relationship
mapping. Additive decoration can enrich an existing relationship but cannot create one or remove its
linkage.

Compound inclusion validates requested paths, includes intermediate resources, applies fieldsets per
resource type, deduplicates by resource identity, preserves first-encounter order, detects cycles,
and enforces traversal limits.

Sparse-fieldset provenance belongs to `MappedDocument`. Writers compose its per-resource linkage
exemptions into validation; callers do not translate provenance into validation policy, and the core
document model does not carry mapping state.

Domain writes preserve a caller-supplied, parameterized Jackson `JavaType`. A generic root whose
binding cannot be recovered fails at the affected member rather than inferring from runtime values.

JSON:API `id` and `lid` are independent roles. `@JsonApiId` maps only `id`,
`@JsonApiLocalId` maps only `lid`, and neither falls back to the other. Inclusion identity tracking
recognizes both aliases of a resource without conflating their protocol meanings.

## Consequences

- Applications explicitly control exposure and traversal; mapping annotations acquire no fetch,
  cascade, repository, or ORM semantics.
- Fieldset exemptions relax full linkage only for the affected resource identities; unrelated defects
  still fail validation.
- Generic write fidelity sometimes requires the declared-type entry point instead of convenience
  inference.
- Local identifiers remain document-scoped protocol identity, not inferred persistence state.
- DTO linkage properties are not a lossless view of relationship presence; the core document retains
  all wire states. Mapping and inclusion semantics are shared in `jsonapi-java-mapping`, while native
  property and conversion mechanics remain adapter-owned ([ADR-022](022-responsibility-based-mapping-and-native-wire-codecs.md)).
