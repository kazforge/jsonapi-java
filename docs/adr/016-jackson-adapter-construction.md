# ADR-016: Mapper-Instance Construction for Jackson Adapters

**Status:** Accepted
**Date:** 2026-08-24
**Amended:** 2026-09-05 (registry coherence construction guarantee; decorator collaborator on the
canonical resource-mapper seam)

## Context

Jackson adapters expose several capabilities with different policy and collaborator needs:
document writing and reading, domain-to-resource mapping, flat binding, typed domain envelopes, and
the two presence-aware PATCH paths. A mapper builder can be turned into a mapper without adding
capability semantics, so accepting both a configured mapper and a builder creates equivalent
construction paths rather than useful abstraction.

The project is pre-first-release. The current Jackson 3 source surface therefore has no released
source or binary compatibility obligation that would justify retaining accidental overloads or
constructors.

## Decision

Jackson-major adapters use a narrow mapper-instance-based construction seam:

```text
fully configured mapper instance
  + capability-specific policy/context
  + genuinely required collaborators
  -> adapter capability
```

For Jackson 3, the canonical public factory forms are:

| Capability | Canonical inputs after the mapper |
|---|---|
| document writer | `ValidationContext` |
| document reader | `DocumentReadContext` |
| resource mapper | `IdentifierConverter`, `ResourceDecoratorRegistry` |
| resource binder | `IdentifierConverter`, relationship linkage mappers |
| typed domain document reader | `DocumentReadContext`, `ResourceTypeRegistry`, `IdentifierConverter`, relationship linkage mappers |
| presence-aware PATCH command reader | `ValidationContext`, `IdentifierConverter`, relationship linkage mappers |
| typed PATCH DTO reader | `ValidationContext`, `IdentifierConverter`, relationship linkage mappers |

`ResourceTypeRegistry` construction requires an explicit configured mapper; the no-argument
default-mapper builder is not part of the API. A consuming typed domain document reader
re-resolves every registered target against its own configured class-level resource metadata when
the reader is constructed and rejects disagreement with `RESOURCE_TYPE_MISMATCH` (no document
location). Registries built from distinct mapper instances remain usable together when their
registered resource-type keys agree; only class-level resource metadata is checked eagerly, not
full property mappings.

The mapper is the caller's configured Jackson authority. Adapter factories do not mutate it. A
capability may derive an isolated mapper internally when its implementation needs adapter modules or
separate introspection state; that is an implementation detail and does not create another public
construction model.

Capability-specific contexts remain separate because they express different stage semantics. A
universal options object is not introduced merely to make factory signatures look uniform.

Convenience factories are allowed when they remove real boilerplate and have unambiguous defaults.
They delegate to the canonical mapper-instance form. The Jackson 3 facade keeps shortcuts for
default validation policies, the default identifier converter, and an empty linkage-mapper set.
`JsonMapper.Builder` overloads that only call `build()` and delegate are not part of the API and are
not retained as deprecated bridges.

## Cross-major and integration policy

Jackson 2 should expose the same capabilities and semantic policy inputs, using its own mapper type
and mechanics. Cross-major parity is semantic capability symmetry plus equivalent configuration
authority, not textual duplication of Jackson 3's convenience overloads.

Future Spring integration should depend on the canonical seam: it supplies its configured mapper,
the capability context, and genuinely required collaborators. It does not reproduce builder
overload matrices, convenience combinatorics, or internal mapper rebuilding, and no Spring-specific
factory is required by this decision.

## Consequences

- Public adapter construction has one obvious external dependency: a configured mapper instance.
- Repository consumers migrate directly to the clean pre-release API; obsolete public paths are
  removed rather than deprecated or aliased.
- Jackson configuration such as modules, mix-ins, naming strategies, serializers, deserializers,
  visibility, and property behavior remains caller-controlled.
- Future Jackson-major and Spring APIs can be designed from stable semantic inputs without
  reverse-engineering Jackson 3 overload history.
- Internal mapper derivation remains capability-specific and is not broadened into a separate
  mapper-isolation redesign.
