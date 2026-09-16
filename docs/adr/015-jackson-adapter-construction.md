# ADR-015: Mapper-Instance Construction for Jackson Adapters

**Status:** Accepted
**Date:** 2026-08-24

## Context

Adapter capabilities need different validation, read, mapping, registry, decoration, and linkage
collaborators. A mapper builder adds no capability semantics beyond producing a mapper, while a
universal options object would hide meaningful stage differences.

## Decision

Public Jackson adapter factories take a fully configured mapper instance, capability-specific
policy or context, and only the collaborators that capability genuinely requires. Builder overloads
that merely call `build()` are not a second public construction model.

The caller's mapper is configured-Jackson authority and is never mutated. A capability may derive an
isolated internal mapper when it needs adapter modules or separate introspection state; that remains
an implementation detail. Convenience factories are permitted only for unambiguous defaults and
delegate to the mapper-instance seam.

Capability contexts stay distinct. `ResourceTypeRegistry` is mapper-neutral: callers register wire
types against `java.lang.reflect.Type`, and the consuming adapter converts and verifies those targets
through its configured mapper.

Jackson 2 and Jackson 3 provide semantic capability symmetry with their native mapper types. Parity
does not require textual duplication of every convenience overload. Future integrations supply their
configured mapper and required collaborators through the same seam.

## Consequences

- Adapter construction has one obvious external authority: the configured mapper instance.
- Modules, mix-ins, naming, visibility, serializers, deserializers, creators, and property behavior
  stay caller-controlled.
- Exact factory signatures and mapper derivation belong to adapter Javadocs and tests rather than
  this decision record.
- Internal mapper isolation remains capability-specific, not a general abstraction layer.
