# ADR-014: Recursive Structured Value PATCH Semantics

**Status:** Accepted  
**Date:** 2026-08-18

## Context

ADR-012 and ADR-013 made presence-aware PATCH operate at the JSON:API resource-member boundary: an
attribute is either omitted or supplied as a whole converted value. That is sufficient for scalar
and atomic structured values, but it cannot express a *partial* update inside a structured value
without losing nested omission state. For example, an application model with
`PatchPresence<AddressPatch>` (where `AddressPatch` has `PatchPresence<String> street` and
`PatchPresence<String> city`) needs the typed PATCH path to represent `street = present(...)` and
`city = omitted()` rather than collapsing the nested object to one fully materialized replacement.

The low-level `PatchCommand<T>` path needs equivalent expressive power: nested structured changes
must not be flattened to string paths or reduced to a fully materialized replacement object when the
request carries meaningful nested omission state.

The capability is first consumed by structured JSON:API attributes, but the recursive machinery must
be structured-value-oriented rather than attribute-hardcoded: a follow-up flat JSON:API meta-mapping
feature will need the same omission/null/value semantics for structured resource and
relationship meta values, and must not introduce a second recursive engine.

## Decision

Define a reusable recursive presence-aware PATCH semantics for structured values, delivered as the
first public use for structured JSON:API attributes on both the typed `PatchPresence<T>` DTO path
and the low-level `PatchCommand<T>` path, with machinery independent of the JSON:API member location.

### Neutral recursive contract (jsonapi-java-jackson-api)

A new Jackson-major-neutral payload, **not** a `PatchChange` variant:

- `StructuredPatch` — an immutable list of `StructuredMember`. A present structured value's
  requested changes; omission is implied by absence (mirrors the top-level `changes()` philosophy).
  An empty list means the structured value was supplied as an explicit empty object (present, zero
  nested changes — never a clear-all).
- `StructuredMember(String wireName, String logicalName, StructuredMemberState state)` — one
  supplied nested member. Both names are carried because they serve different purposes: `wireName`
  is the document member name (used for wire lookup and diagnostics), `logicalName` is the Jackson
  property name on the application type (used for application-property correspondence). There is
  deliberately no single ambiguous `name`.
- `StructuredMemberState` — sealed algebra with `Atomic(@Nullable Object value)` and
  `Structured(List<StructuredMember> members)`, constructor-validated so contradictory states are
  unrepresentable. `Atomic` values freeze with the same shallow-freeze convention as `PatchChange`.

On the low-level path a structured attribute's change stays the existing
`PatchChange.AttributeChange(jsonapiName, logicalName, value)` with `value` = the `StructuredPatch`
instance (discriminated via `instanceof`); explicit parent null stays `AttributeChange(value =
null)`. There is **no `PatchChange` sealed-hierarchy change**; the new public payload types are
additive.

### Engine (Jackson 3 internal, location-agnostic)

A new internal `StructuredValueBinder` owns member resolution, shape classification, nested
conversion, null policy, wire-pointer accumulation, and lazy nested declaration validation. It has
no `ResourceMapping` / `MappingProperty` / `@JsonApiAttribute` / `PatchChange` dependency: callers
supply the declared `JavaType`, wire value, starting pointer, and (low-level) accessor, so a later
structured `meta` mapping can reuse the same machinery at its own location.

- **Member resolution** is deserialization-side Jackson introspection (`BeanDescription` from the
  deserialization config), including creator parameters (records / constructor-bound beans) and
  ordinary JavaBean property binding (getter/setter or field). A member is visible iff Jackson would
  bind it from an object. The wire name of a member is its Jackson-resolved name (explicit
  `@JsonProperty` or the naming strategy applied to the logical property name). Each member carries
  both its serialization-side member (`getAccessor()`: getter, then field) and its
  deserialization-side member (`getMutator()`: creator parameter, then setter, then field) because
  Jackson may surface property-scoped annotations on different accessors; the member's resolved
  `JavaType` always comes from the deserialization-side primary type, never inferred from a single
  accessor. Member and shape resolution preserve full generic `JavaType` bindings recursively (a
  `Box<Integer>` member resolves as `List<Integer>`), so raw-`Class` resolution would fail the
  resulting atomic conversion.
- **Shape classification** is cached per `JavaType` plus the deserialization-config hash (naming
  strategy and visibility checker), independently of the serialization-keyed resource-mapping cache.
  A type is traversable only when it resolves to a property/creator-driven bean deserializer
  (`BeanDeserializerBase`); scalars, enums, primitives, `String`, `java.time`/`java.math`/
  `java.util.UUID`-style types, custom/scalar deserializers, and containers stay atomic.
- **Typed mode** classifies shapes as *presence-aware* (every visible member is exactly
  `PatchPresence<T>`), *mixed* (some presence attempt but not every member is exactly
  `PatchPresence<T>`), or *ordinary* (no presence-aware members). Recursion enters only presence-aware
  shapes; mixed shapes are invalid declarations; ordinary beans convert atomically.
- **Low-level mode** recurses into *any* traversable structured bean when the supplied wire value is
  a JSON object; a non-object wire value for any declared type is atomic conversion through the
  configured Jackson authority (this pins single-component-record behavior: object wire traverses,
  scalar wire converts).

### Typed path: opt-in presence-aware recursion

Typed recursion is an opt-in declaration: a nested member whose inner type is a presence-aware PATCH
shape (every visible member exactly `PatchPresence<T>`, no wrapper-level `@JsonDeserialize` /
`@JsonSerialize` customization) recurses when its wire value is a JSON object. Construction uses the
single-pass strategy already established by ADR-013: the binder builds a **complete nested
`PresenceMarker` tree** (each nested level a marker map whose values are markers) and performs a
single whole-tree `convertValue` against the typed PATCH DTO target. This preserves the strict
`PatchPresenceDeserializer` marker invariant and never introduces a `PatchPresence` serializer
solely for internal construction. Nested marker maps are keyed by each member's Jackson-resolved
wire name — the same names Jackson binds from a real document — so the marker tree deserializes
under any caller naming strategy or explicit rename.

- Wire shape rule: JSON object → recurse; JSON `null` → normal outer/nested `PatchPresence` null
  semantics; non-object non-null wire value (string/array/scalar) → configured Jackson atomic
  conversion against the declared inner target, failing with `UNSUPPORTED_ATTRIBUTE_VALUE` if
  incompatible.
- `Optional<X>` unwrap/rewrap is pinned: member inner type `Optional<X>` — wire omission →
  `PatchPresence.omitted()`; wire `null` → `PatchPresence.present(Optional.empty())`; wire object →
  `PatchPresence.present(Optional.of(recursivelyBoundX))`; the same rule applies at every level.
- Nested declaration validation is lazy: a nested shape's declaration is resolved and validated only
  when that structured member is actually bound, so arbitrary type graphs are not traversed for
  declaration validation. Once a nested shape is entered, every visible member must satisfy the
  strict presence-aware contract (raw `PatchPresence`, direct `Present<T>`, and wrapper-level
  customization are invalid declarations).

### Low-level path: ordinary-domain recursion

The low-level path derives supplied-only nested changes from **ordinary structured domain value
types** under the traversable-bean + object-wire boundary, with no application PATCH DTO required.
`Optional<X>` is a **transparent qualification wrapper**: it is unwrapped for the traversal decision;
if `X` qualifies and the wire value is an object, the engine recurses into `X` and the resulting
`StructuredPatch` is **not** wrapped in `Optional` (the wrapper belongs to the declared application
type; `StructuredPatch` is a requested-change representation, not a replacement value). A single
`PatchPresence<T>` wrapper is unwrapped on the low-level path too, so a currently-failing declaration
style becomes supported for both scalar and structured members.

**Presence-aware PATCH shapes remain a typed-path concept.** On the low-level path, a
`PatchPresence<T>` member whose inner type is a presence-aware PATCH shape fails loudly with
`INVALID_PATCH_PROPERTY_TYPE` at the accumulated pointer rather than implicitly registering
`PatchPresenceModule` or inventing new product semantics. The typed and low-level declaration rules
intentionally differ because the typed path materializes an application-owned PATCH DTO while the
low-level path represents requested changes independently of a PATCH DTO.

### Shared semantics

- **Nested null policy:** top-level low-level `RAW_NULL` is an outer-location rule. Once recursion
  enters a structured value, explicit `null` for a nested member is converted through that member's
  declared inner target type on **both** paths: a nested `Optional<X>` member becomes
  `Atomic(Optional.empty())` (typed: `Present(Optional.empty())`), and explicit `null` for a
  primitive inner type is rejected with `UNSUPPORTED_ATTRIBUTE_VALUE`.
- **Empty-object semantics:** an explicitly supplied empty structured object is a present structured
  patch with zero nested changes (typed: a shape with every member `Omitted`); it is never a
  clear-all or delete operation. Nested explicit `null` is a supplied value under the nested target's
  null contract; it is not a generic remove/delete-key protocol.
- **Container boundaries:** `List`, `Set`, array, and `Map` inner types are atomic replacement
  values on both paths; there is no partial-element or map-key deletion semantics.
- **Pointer ownership:** the engine accumulates wire-name pointers from the caller-supplied starting
  pointer (e.g. `/attributes/address/street`), and every nested failure uses the accumulated pointer.
  Final Jackson construction failures from the single-pass typed `convertValue` have their deep
  Jackson path translated through the already-resolved shape metadata to the corresponding wire-name
  pointer (a narrow shape-aware step that never relies on innermost-only property recovery).
- **Unknown nested members:** the typed path rejects them with `UNKNOWN_PATCH_MEMBER`; the low-level
  path skips them to stay a lossless change list (mirroring the top-level rules).
- **Nested member-name resolution:** the engine resolves a supplied nested member by its
  Jackson-resolved wire name only. `wireName` is the document member name used for wire lookup and
  diagnostics and is the canonical nested member identity on the wire; `logicalName` is carried in
  `StructuredMember` solely for application-property correspondence and is never treated as an
  automatic JSON input alias. Under a naming strategy (for example SNAKE_CASE) only the
  Jackson-resolved wire name binds; the Java logical name is not an alias and either fails on the
  typed path (`UNKNOWN_PATCH_MEMBER` at the supplied wire pointer) or is skipped on the low-level
  path. This keeps the `wireName`/`logicalName` distinction unambiguous. If real Jackson aliases
  (`@JsonAlias`) are later supported, they would be resolved from Jackson's actual deserialization
  alias metadata, never approximated via the internal name.
- **Nested atomic conversion authority:** nested atomic conversion preserves the applicable
  configured Jackson deserialization semantics. On the low-level path, property-scoped
  customization (`@JsonDeserialize using = ...`, converters, content/key deserializers, and type
  refinement as Jackson applies them during normal binding) is handled by the location-neutral
  `PropertyScopedValueConverter`, which resolves the containing bean's fully-contextualized
  `SettableBeanProperty` and delegates to `SettableBeanProperty.deserialize`, preserving the
  property's `TypeDeserializer` (polymorphic values) and null provider; members without a bean-based
  property fall back to ordinary `convertValue` (type-level and module authority still apply).
  On the typed path, nested atomic values remain JSON-compatible values in the `PresenceMarker`
  tree, and the contextual `PatchPresence` deserializer reads each inner type exactly once. Typed
  wrapper-level customization is rejected, while inner-type deserializers and modules remain
  authoritative through that contextual read. Whether a low-level member is considered customized
  is decided from Jackson's effective deserialization and serialization property metadata on both
  the serialization-side and deserialization-side members (setter-, creator-parameter-, field-,
  and getter-placed `@JsonDeserialize` / `@JsonSerialize` are all detected, symmetrically).
  Type-refinement checks use the member's resolved property `JavaType` (the deserialization-side
  primary type), never `AnnotatedMember.getType()` — for a setter that returns the method return
  type (`void`) instead of the setter parameter type, which makes `as`/`contentAs`/`keyAs`
  refinement detection incorrect. On the low-level path a bean-valued property with a
  property-scoped deserialization customization stays an atomic converted member (the custom
  deserializer is applied), never a recursed `StructuredPatch`, while the surrounding bean still
  recurses. Explicit nested null also converts through the containing property's
  `SettableBeanProperty.deserialize`, so a property-level null provider (for example
  `@JsonSetter(nulls = Nulls.AS_EMPTY)`) applies rather than the root target deserializer's null
  value; only the primitive-null rejection is handled locally. A property-level `TypeDeserializer`
  (polymorphic values) is likewise preserved through the nested atomic conversion.
- **Outer-state policy:** the engine is policy-free; the owning JSON:API mapping location constrains
  which outer presence states are wire-valid. Attributes allow `Present(null)`; a later `meta`
  mapping may reject an outer `Present(null)` while still allowing nested `Present(null)` values.

### Rejected alternatives

- **A `PatchChange` sealed variant for structured values.** Rejected: it would couple the neutral
  change hierarchy to one structured-value representation and make `meta` reuse awkward; the chosen
  `StructuredPatch` payload is additive and location-neutral.
- **A per-level `convertBean`/per-member construction strategy.** Rejected: it would break the strict
  marker invariant or require a `PatchPresence` serializer; the single whole-tree `convertValue`
  preserves ADR-013's construction authority.
- **Treating presence-aware shapes as the only recursion rule on the low-level path.** Rejected: it
  would require ordinary domain types to become PATCH DTOs to express partial updates; the
  traversable-bean rule keeps the low-level path DTO-free.

## Consequences

- Applications can express partial structured-attribute updates on both PATCH paths, and the two
  paths carry the same nested omission/null/value semantics for the same wire request.
- The low-level path's bean-typed attribute values change: a supplied object for a traversable
  structured bean now binds to a `StructuredPatch` of supplied-only nested changes instead of a fully
  materialized replacement bean. This is intentional and necessary to preserve nested omission
  information; there is **no opt-out for this behavior**. Migration: scalar/atomic values continue to receive
  converted application values; recursively structured values are inspected through `StructuredPatch`.
  Scalar attributes, containers under the atomic boundary, identifiers, and relationships keep their
  existing behavior.
- Typed recursion is opt-in through presence-aware nested PATCH shapes; mixed shapes, raw
  `PatchPresence`, direct `Present<T>`, and wrapper-level customization are rejected declarations,
  validated lazily when the nested shape is bound.
- The engine is location-neutral and reusable: structured `meta` mapping (ADR-015) applies the same
  `StructuredPatch` payload and engine entry points at its own location with a stricter outer-state
  policy, without a second recursion model.
- Recursive structured values are not recursive JSON:API relationship graph mutation; relationships
  remain linkage-oriented atomic replacement.
- The neutral contracts stay Jackson-import-free and the engine stays adapter-internal, with each
  adapter pinned to its module's Jackson line while both consume the same contracts.
