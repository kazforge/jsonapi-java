# ADR-011: Presence-Aware Resource PATCH Binding

**Status:** Accepted
**Date:** 2026-07-30

## Context

JSON:API resource updates are not JSON Merge Patch. An omitted member is not an explicit null, and
constructing a complete DTO from a partial update can fabricate values for immutable objects.
Applying updates directly would also take on authorization, mutation, and persistence policy.
Nested structured values require the same presence distinction without making relationship linkage
an object-graph mutation protocol.

## Decision

Validate an update document before either PATCH projection: primary `data` must be one resource
object with `type` and `id`, not absent, null, an identifier, or a collection. The caller may supply
an expected endpoint identity. Omitted attributes and relationships request no change; a supplied
relationship requires `data` and replaces the whole linkage, distinguishing null, single, and empty
or populated collections. Neither projection reads `included` or applies changes to application
state. Identity comes from `id`, never `lid`.

Offer two projections of the validated update:

- The low-level `PatchCommand` contains only supplied mapped changes. It skips unknown members but
  rejects a supplied mapped member without an effective Jackson deserialization target. Resource
  and relationship meta have location-specific change variants; relationship meta participates only
  alongside supplied relationship `data`.
- The opt-in typed PATCH DTO uses an application-owned annotated schema whose patchable properties
  are `PatchPresence<T>`: omitted, present null, or present value. Its identity is unwrapped. It
  rejects unknown supplied members and invalid presence-wrapper declarations rather than projecting
  through a normal read/write DTO. Configured Jackson owns construction and inner-value conversion;
  an inner `Optional<T>` does not erase outer presence.

For traversable structured attributes and resource-side meta, represent supplied nested members as
`StructuredPatch` with `StructuredMember` entries retaining configured wire name, logical property
name, and atomic or nested state. A supplied empty object is not a clear-all. Typed recursion is
opt-in through all-presence-aware nested shapes and rejects unknown members; low-level recursion
traverses ordinary beans and skips unknown members. Scalars, custom atomic values, lists, sets,
arrays, and maps replace as whole values. Nested null follows declared conversion (and fails for
primitives); it is never a generic remove operation. Configured property-scoped conversion can keep
an otherwise bean-shaped value atomic. Outer attributes may be null, but object-valued meta may
not. Relationships remain atomic linkage replacements, including identifier meta carried by that
linkage ([ADR-014](014-flat-whole-object-meta-mapping.md)).

Backend-neutral orchestration is shared; native shape discovery, conversion, and DTO construction
remain with each adapter ([ADR-022](022-responsibility-based-mapping-and-native-wire-codecs.md)).

## Consequences

- Omission, explicit null, supplied empty objects, and relationship replacement stay observable.
- Typed PATCH DTOs are distinct from ordinary read/write DTOs; strict typed versus permissive
  low-level unknown-member handling is deliberate.
- Applications authorize, validate business invariants, and apply either projection themselves.
