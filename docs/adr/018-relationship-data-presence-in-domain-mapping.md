# ADR-018: Ordinary Domain Relationships Remain Linkage-Oriented

**Status:** Accepted
**Date:** 2026-09-03

## Context

The core model and document codec preserve all relationship-data states: absent `data`, explicit
null linkage, single linkage, and collection linkage including an empty collection. Links and meta
are independent. Ordinary annotated DTO mapping is intentionally narrower and needs a stable boundary
before higher-level application APIs depend on it.

## Decision

Ordinary `@JsonApiRelationship` properties remain linkage-oriented. When a selected mapped
relationship is written, it always contains `data`: null or empty optional produces explicit null
to-one linkage, an empty to-many value produces an empty collection, and populated values produce
their corresponding linkage.

On flat reads, a wire relationship without `data` leaves the linkage property unbound under
configured-Jackson missing-property semantics. Explicit `data: null` remains a distinct explicit-null
input, and is a cardinality error for a to-many property. Relationship meta can still bind through
`@JsonApiRelationshipMeta` independently of linkage.

Links-only and meta-only relationships remain first-class core/document representations in both
directions, but ordinary DTO mapping and the Level-1 relationship facet do not gain a presence
wrapper or relationship envelope for them. Applications that must create or inspect those forms use
the document-level APIs. Additive decoration may enrich an existing relationship but cannot create
one or remove its linkage.

This is a representation-layer decision, not HTTP policy. Operation-specific core validation may
further constrain relationships, such as requiring `data` on supplied primary relationships in a
create request.

## Consequences

- Ordinary relationship DTOs stay plain and do not acquire a fourth-state wrapper.
- DTO writes cannot produce links-only relationships; explicit document construction is the accepted
  escape hatch.
- A DTO linkage property is not a lossless relationship-presence view. The core document always
  retains the wire distinction.
- Mapping, relationship meta, decoration, inclusion, fieldsets, and PATCH keep separate authorities.
- [ADR-019](019-level-one-application-api-contract.md) freezes this linkage-oriented boundary into
  the neutral Level-1 contract without replacing this decision.
