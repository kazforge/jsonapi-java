# ADR-014: Recursive Structured Value PATCH Semantics

**Status:** Accepted
**Date:** 2026-08-18

## Context

Top-level PATCH presence is insufficient for partially updating a structured attribute or meta
object: materializing a whole replacement loses which nested members were omitted. Typed PATCH DTOs
and low-level commands need one neutral nested-change model without turning relationship linkage into
an object graph mutation protocol.

## Decision

`StructuredPatch` represents a supplied structured value as its supplied `StructuredMember` values.
Each member retains both its Jackson-resolved wire name for lookup and diagnostics and its logical
name for application-property correspondence. Its state is either an atomic value or another
structured member list. An empty structured patch means an explicitly supplied empty object, not
clear-all.

Each Jackson adapter implements the same location-neutral recursive binding contract with native
Jackson introspection:

- The typed path recurses only into an opt-in presence-aware shape whose visible members are exactly
  `PatchPresence<T>`. Mixed shapes, raw presence wrappers, and wrapper-level customization are invalid.
- The low-level path recurses into traversable ordinary bean values when the wire value is an object.
  It does not require an application PATCH DTO.
- Scalars, custom atomic values, `List`, `Set`, arrays, and maps are whole-value replacements.
  Containers have no element-addressed or map-key deletion semantics.
- Nested explicit null converts through the declared property contract; primitive null fails. Null is
  never a generic remove operation.
- Typed binding rejects unknown nested members; low-level binding skips them. Both resolve input by
  configured-Jackson wire names and preserve generic type bindings.
- Property-scoped Jackson conversion remains authoritative. A custom property deserializer can keep
  an otherwise bean-shaped low-level member atomic.

Outer presence rules remain with the owning JSON:API location: attributes may be explicitly null,
while object-valued meta may not.

[ADR-015](015-flat-whole-object-meta-mapping.md) reuses this payload and recursion contract for meta.
Under the combined rule, `PatchChange` has location-specific resource-meta and relationship-meta
variants, while `StructuredPatch` remains a payload rather than a structured-value-specific variant.

## Consequences

- Both PATCH paths preserve nested omission, null, supplied-value, and empty-object distinctions.
- On the low-level path, an object supplied for a traversable bean yields `StructuredPatch` rather
  than a materialized replacement bean. This behavior has no opt-out; atomic values and containers
  retain replacement semantics.
- Typed recursion is declaration-driven and may fail when the nested shape is first used.
- Relationships remain linkage-oriented atomic replacements; recursion does not mutate a resource
  graph.
- Neutral payload contracts stay Jackson-import-free while the recursive engine remains
  adapter-internal.
