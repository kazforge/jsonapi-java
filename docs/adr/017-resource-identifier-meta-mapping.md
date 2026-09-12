# ADR-017: Opt-in RelationshipLinkage for Resource Identifier Meta

**Status:** Accepted
**Date:** 2026-08-27
**Amendment:** 2026-08-31 — `@JsonApiRelationshipMeta` associates by Jackson property identity

## Context

ADR-015 maps the complete `meta` object of a resource (`ResourceObject.meta`) and of a relationship
(`Relationship.meta`) onto sibling application-owned properties. JSON:API also allows `meta` on
`ResourceIdentifier` objects used as relationship linkage. That member is not relationship meta: it
belongs to one specific linkage occurrence.

For to-one linkage there is at most one identifier and therefore at most one identifier-meta object.
For to-many linkage each identifier may carry its own `meta`. Treating that per-element member as
`Relationship.meta` would conflate two JSON:API concepts. Inventing generic list-element PATCH
addresses so that identifier meta could be patched independently would reopen ADR-014's atomic
`List` / `Set` / array / `Map` replacement boundary.

An initial sibling-property design (`@JsonApiIdentifierMeta("relationship")` next to
`@JsonApiRelationship`) was implemented on an unmerged branch and rejected before merge. To-many
mapping required a hidden positional association between an independent relationship collection and
an independent meta collection. That model cannot own per-linkage state: `Set` has no stable order,
length can diverge, and the association is not structural. This ADR records the replacement design,
not that discarded API.

The wire codec already reads and writes `ResourceIdentifier.meta`. This ADR defines the
application-owned mapping contract against the stabilized Jackson 3 / `jackson-api` surface so
Jackson 2 can later reproduce the same semantics.

## Decision

Introduce an opt-in, Jackson-major-neutral wrapper:

```java
public record RelationshipLinkage<T, M>(T target, @Nullable M meta) {}
```

in `jsonapi-java-jackson-api` (`com.kazforge.jsonapi.jackson.mapping`). The wrapper represents one
relationship linkage occurrence. Applications that do not need identifier meta keep ordinary
relationship shapes unchanged.

### Public API

`RelationshipLinkage` is not a JSON:API resource and is not discovered as a domain type.
`target` is required (compact constructor). Absent to-one linkage is a Java `null` relationship
property, not a wrapper with a null target.

`meta` maps exclusively to that occurrence's `ResourceIdentifier.meta`. It is not
`Relationship.meta` (still `@JsonApiRelationshipMeta`) and not `ResourceObject.meta` (still
`@JsonApiMeta`).

The three locations stay distinct:

```text
ResourceObject.meta          -> @JsonApiMeta
Relationship.meta            -> @JsonApiRelationshipMeta(relationship = "name")
ResourceIdentifier.meta      -> RelationshipLinkage<T, M>
```

`@JsonApiRelationshipMeta` is unchanged. The discarded `@JsonApiIdentifierMeta` annotation is not
part of the public API.

### Transparency around `target`

The wrapper is not a second relationship-mapping pipeline. Implementations unwrap `target`, run the
existing relationship mapping for `T`, then overlay identifier meta `M` onto the resulting
`ResourceIdentifier`.

Everything that applies to an ordinary relationship target continues to apply to the wrapped target
where relevant: resource type resolution, id / lid extraction, `ResourceTypeRegistry`, configured
Jackson, custom `RelationshipLinkageMapper` dispatch on `T`, generic `JavaType`, inclusion
traversal, diagnostics, null handling, and existing relationship mapping rules. Custom linkage
mappers are not required to know about `RelationshipLinkage`; they receive `T`.

### Cardinality and containers

Supported wrapper/container shapes are the same containers already supported for ordinary
relationship targets:

- `RelationshipLinkage<T, M>` (to-one)
- `List<RelationshipLinkage<T, M>>`
- `RelationshipLinkage<T, M>[]`
- `Optional<RelationshipLinkage<T, M>>`
- `Set<RelationshipLinkage<T, M>>`

`Set` is allowed. The sibling design rejected `Set` because two independent sequences cannot be
aligned without order. Once target and meta live in the same wrapper element, that problem does not
exist.

To-many ownership is structural: each `RelationshipLinkage` element carries the meta that belongs
to that exact identifier. There is no parallel positional meta collection.

`M` follows ADR-015's whole-meta object rule: Bean / `Map` / `Object`, with at most one `Optional`
wrapper. Nested `RelationshipLinkage` as `T`, and raw erased wrappers, are declaration failures.

### Null and absence

`meta == null` supplies no overlay: the target is mapped normally. If the target is already a
`ResourceIdentifier` that carries `meta`, that existing identifier meta is preserved. A non-null
`meta` is an authoritative overlay.

Callers are not required to wrap `M` in `Optional` merely to express absent identifier meta.

A configured serializer that emits nothing is treated as no overlay (same preservation as
`meta == null`).

### Read and write

Read: convert the identifier through the existing target mapping for `T`, convert
`ResourceIdentifier.meta` through configured Jackson against the full generic `JavaType` of `M`,
and construct `RelationshipLinkage<T, M>`. Each to-many wrapper element receives the meta belonging
to that exact resource identifier. Custom to-many `RelationshipLinkageMapper` invocations for
wrapped properties therefore map each identifier occurrence as `T` (`SingleLinkage`) before
constructing that element's wrapper; collection-level mapper results are not reassociated by index.

Write: map `target` exactly as an ordinary relationship target; serialize `M` through configured
Jackson (full `JavaType`); require a JSON object; overlay the resulting `Meta` onto the constructed
identifier. Unrelated identifier state is preserved:

```text
type, id, lid, additionalMembers  stay
meta                              becomes the deliberate overlay result
```

Read-side built-in `ResourceIdentifier` conversion still drops additional members
(`copyLinkageIdentifier`). That conversion-scope policy is independent of write overlay.

Property-scoped Jackson customization on the relationship property applies to that property where it
is applicable. Identifier-meta conversion uses the wrapper's meta `JavaType` and type/module
serializers; it does not invent a second Jackson configuration path. Mapper derivation and module
registration stay consistent with ADR-016.

### PATCH

Identifier meta is **not independently patchable**.

- No `PatchChange` variant is added.
- Typed `PatchPresence<RelationshipLinkage<T, M>>` is a whole-linkage replacement, including any
  identifier meta on that wrapper.
- Low-level `PatchCommand` carries identifier meta on converted relationship values
  (`ResourceIdentifier` and/or `RelationshipLinkage`) inside `RelationshipChange`.
- To-many replacement replaces the entire linkage collection. There is no element-addressed mutation
  of identifier meta inside ADR-014 atomic containers.

### Inclusion

If an ordinary `@JsonApiRelationship Person author` participates in inclusion traversal, then
`RelationshipLinkage<Person, AuthorMeta>` traverses the `Person` target the same way. The wrapper
itself is not an includable resource. To-many collections traverse each wrapped target.

### Diagnostics

- `INVALID_IDENTIFIER_META_TARGET` — wrapper declaration/shape failures (nested wrapper, invalid
  `M` object shape).
- Raw erased `RelationshipLinkage` fails as `UNRESOLVED_GENERIC_TYPE` at the relationship `data`
  location, the same unresolved-generic rule as other relationship targets.
- Converted runtime values that are not a JSON object, and invalid JSON:API meta members, reuse
  `INVALID_META_TARGET` (ADR-015 taxonomy). Declaration failures and conversion failures stay
  distinct.

Locations remain resource-relative `MappingLocation` pointers:
`/relationships/{name}/data/meta` (to-one / declaration) and
`/relationships/{name}/data/{index}/meta` (to-many element).

### Ownership

`RelationshipLinkage`, diagnostic codes, and other contracts live at Jackson-major-neutral
boundaries. Jackson 3 owns introspection, unwrap/overlay, property-scoped conversion, and
adapter-local mechanism tests. `jsonapi-java-jackson-api` remains free of Jackson-major imports.

## Consequences

- Applications that need per-linkage identifier meta opt in with a structural wrapper. Ordinary
  relationship shapes remain source- and behavior-compatible.
- To-many target/meta association cannot silently desynchronize: it is the wrapper element.
- Presence-aware PATCH can carry identifier meta only by replacing the relationship's linkage.
- Built-in `ResourceIdentifier` conversion preserves identifier `meta` on read and still drops
  additional members. Write overlay preserves additional members.
- Exhaustive `PatchChange` switches do not gain a new variant. Jackson 2 parity must reproduce this
  contract rather than invent a more granular PATCH model.

## Non-goals

- Redesigning `@JsonApiRelationshipMeta` or `@JsonApiMeta`.
- Reopening ADR-014 / KAZ-76 container PATCH semantics or adding element-addressed mutation.
- Persistence lookup, hydration, authorization, or relationship-graph loading.
- Requiring `RelationshipLinkage` for relationships that do not use identifier meta.
- A general-purpose relationship envelope (links, inclusion flags, PATCH state, relationship meta).
- Jackson 2 implementation in this increment.
