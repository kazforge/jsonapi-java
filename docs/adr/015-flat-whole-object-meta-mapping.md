# ADR-015: Flat Whole-Object Mapping for Resource-Side Meta

**Status:** Accepted
**Date:** 2026-08-19

## Context

Application DTOs may need resource and relationship `meta`, but those complete JSON:API objects are
different from document meta and from per-identifier meta. They also need the same read, write, and
presence-aware PATCH treatment as other mapped resource members without introducing another
recursive update model.

## Decision

Both Jackson adapters map one whole application-owned object per resource-side meta location:

- `@JsonApiMeta` owns `ResourceObject.meta`;
- `@JsonApiRelationshipMeta` owns one mapped `Relationship.meta` and associates with its relationship
  by Jackson property identity;
- `ResourceIdentifier.meta` remains separate and is owned by
  [ADR-017](017-resource-identifier-meta-mapping.md).

There is at most one owner for each location; values are not merged and there is no last-wins rule.
Configured Jackson owns property conversion, while mapping enforces that meta is object-valued.
Absent meta leaves the property unbound and an empty object remains present.

Normal read/write and low-level PATCH accept an ordinary bean, map, or object target with at most one
`Optional` wrapper. Typed PATCH requires exactly `PatchPresence<T>` before that target. Atomic
map-like targets remain whole replacements; traversable beans reuse the recursive contract from
[ADR-014](014-recursive-structured-value-patch-semantics.md).

Resource meta is independent of sparse fieldsets. Relationship meta rides its relationship and is
omitted when the relationship is fieldset-excluded. On PATCH, relationship meta participates only
when relationship `data` is present; readers do not synthesize a meta-only relationship change.

Low-level PATCH uses location-specific `ResourceMetaChange` and `RelationshipMetaChange` variants.
Together with [ADR-014](014-recursive-structured-value-patch-semantics.md), the current rule keeps
location identity in those variants while `StructuredPatch` remains a reusable payload. An attribute
named `meta` cannot by itself identify which JSON:API location changed.

## Consequences

- Resource and relationship meta are available on read, write, and both PATCH paths without an
  envelope or second recursion engine.
- Outer `meta: null` is invalid, while nested members retain their declared null semantics.
- Typed PATCH remains strict for unmapped meta; the low-level path skips meta it cannot represent.
- Adding the two sealed `PatchChange` variants requires exhaustive consumers to handle them.
- Document meta and identifier meta retain separate owners.
