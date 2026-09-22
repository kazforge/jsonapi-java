# ADR-016: Opt-in RelationshipLinkage for Resource Identifier Meta

**Status:** Accepted
**Date:** 2026-08-27

## Context

`ResourceIdentifier.meta` belongs to one linkage occurrence. It is neither `Relationship.meta` nor
`ResourceObject.meta`, and a separate positional meta collection cannot safely stay aligned with
to-many linkage, especially for sets.

## Decision

Applications opt into identifier meta with the neutral structural wrapper
`RelationshipLinkage<T, M>`, where `target` is the ordinary relationship target and `meta` belongs
to that exact identifier occurrence. Ordinary relationships remain unchanged when identifier meta
is not needed.

The three locations remain distinct:

- `ResourceObject.meta` → `@JsonApiMeta`;
- `Relationship.meta` → `@JsonApiRelationshipMeta`;
- `ResourceIdentifier.meta` → `RelationshipLinkage<T, M>`.

The wrapper is transparent around `target`: configured-Jackson conversion, resource type and
identity resolution, custom linkage mapping, inclusion, cardinality, generic type bindings, and
diagnostics follow the ordinary relationship path. To-many containers hold one wrapper per linkage,
so target and meta cannot desynchronize.

On write, the identifier-meta contract is whole-value. A null wrapper `meta` requests no conversion
and preserves any identifier meta already present on a direct identifier. When the wrapper carries a
value, the declared `M` converts through the configured authority: conversion non-emission (a
suppressed or absent value) preserves existing identifier meta, an emitted JSON `null` is an
authoritative overlay that clears it, and an emitted object replaces it wholesale. Any other emitted
shape fails as an invalid meta target. On read, each identifier's meta converts to the declared `M`
while ordinary target conversion remains in force.

Identifier meta is not independently patchable. Typed PATCH replaces the whole
`RelationshipLinkage`; low-level PATCH carries it within `RelationshipChange`; to-many PATCH replaces
the whole atomic collection. No identifier-meta `PatchChange` variant or element-addressed update is
introduced.

## Consequences

- Per-linkage target and meta ownership is structural rather than positional.
- Applications pay the wrapper cost only for relationships that need identifier meta.
- Relationship, resource, and identifier meta retain separate mapping and PATCH boundaries.
- Wrapped targets remain includable, but the wrapper itself is not a resource.
- Both Jackson adapters implement the same neutral contract; Jackson-specific unwrap, conversion,
  and overlay mechanics remain adapter-local.
