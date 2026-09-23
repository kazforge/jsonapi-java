# ADR-013: Recursive Structured Value PATCH Semantics

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

Both Jackson adapters share one location-neutral recursive binding implementation, over each
adapter's native Jackson introspection:

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

[ADR-014](014-flat-whole-object-meta-mapping.md) reuses this payload and recursion contract for meta.
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
- Neutral payload contracts stay Jackson-import-free. The recursive engine is one backend-neutral
  implementation in `jsonapi-java-mapping`; each Jackson adapter supplies configured shape discovery
  and native atomic conversion over an unsupported capability bridge (see
  [ADR-019](019-jackson-neutral-implementation-helpers.md)).

## Partial supersession

The earlier statement that the recursive engine remained adapter-internal is superseded.
JSON:API location, presence, and nested-shape **policy** are now owned once by
`com.kazforge.jsonapi.mapping.internal.StructuredPatchBinder`, together with the neutral resolved
shape value. Each adapter keeps adapter-owned native mechanics only: configured Jackson
shape discovery and caching, wrapper-customization facts, property-scoped conversion, and
construction-path translation, supplied through
`com.kazforge.jsonapi.mapping.internal.StructuredShapeBackend`. The semantics in the Decision —
typed versus low-level recursion boundaries, atomic containers, null handling, strict typed versus
skip low-level unknown members, and property-scoped conversion authority — are unchanged.

The typed `PatchPresence<T>` DTO orchestration is likewise shared policy, owned by
`com.kazforge.jsonapi.mapping.internal.TypedPatchBinder` over an unsupported
`TypedPatchBackend` capability interface; the typed DTO projection itself remains the adapter's
serialization-oriented mapping. See [ADR-019](019-jackson-neutral-implementation-helpers.md).
