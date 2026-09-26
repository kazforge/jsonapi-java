# ADR-014: Distinct Whole-Object Meta Locations

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
- `ResourceIdentifier.meta` belongs to one linkage occurrence, carried by an opt-in
  `RelationshipLinkage<T, M>` wrapper around its ordinary relationship target. To-many wrappers
  keep target and meta paired even for unordered containers.

There is at most one owner for each location; values are not merged and there is no last-wins rule.
Configured Jackson owns property conversion, while mapping enforces that meta is object-valued.
Absent meta leaves the property unbound and an empty object remains present.

Normal read/write and low-level PATCH accept an ordinary bean, map, or object target with at most one
`Optional` wrapper. Typed PATCH requires exactly `PatchPresence<T>` before that target. Atomic
map-like targets remain whole replacements; traversable beans reuse the recursive contract from
[ADR-011](011-resource-patch-binding.md).

Resource meta is independent of sparse fieldsets. Relationship meta rides its relationship and is
omitted when the relationship is fieldset-excluded. On PATCH, relationship meta participates only
when relationship `data` is present; readers do not synthesize a meta-only relationship change.

Low-level PATCH uses location-specific `ResourceMetaChange` and `RelationshipMetaChange` variants;
`StructuredPatch` is a reusable payload, not a location-specific change. Identifier meta is not
independently patchable: typed PATCH replaces the whole wrapper, and low-level PATCH carries it
inside `RelationshipChange`. An attribute named `meta` does not identify a meta location.

On write, a null wrapper meta preserves existing meta on a direct identifier. A non-null wrapper
value converts through declared `M`: non-emission preserves existing meta, emitted null clears it,
and an emitted object replaces it wholesale; other emitted shapes fail. On read, each identifier's
meta converts to declared `M` while its target follows ordinary relationship binding. The shared
mapping writer owns the overlay policy; configured conversion stays adapter-owned
([ADR-022](022-responsibility-based-mapping-and-native-wire-codecs.md)).

## Consequences

- Resource and relationship meta are available on read, write, and both PATCH paths without an
  envelope or second recursion engine.
- Outer `meta: null` is invalid, while nested members retain their declared null semantics.
- Typed PATCH remains strict for unmapped meta; the low-level path skips meta it cannot represent.
- Adding the two sealed `PatchChange` variants requires exhaustive consumers to handle them.
- Document meta and identifier meta retain separate owners; wrapped targets remain includable, but
  the wrapper itself is not a resource.
