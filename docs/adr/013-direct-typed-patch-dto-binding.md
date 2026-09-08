# ADR-013: Direct Typed PATCH DTO Binding

**Status:** Accepted  
**Date:** 2026-08-17  
**Amendment:** 2026-08-31 — unannotated ordinary properties do not participate; conventional `id` remains the identifier convention

## Context

ADR-012 introduced presence-aware resource updates bound into an immutable `PatchCommand`:
only supplied mapped members become typed changes, and applications translate the command into
domain mutations. For applications that prefer a familiar annotated DTO, translating a command
still means writing a projector from `PatchCommand<R>` to their own DTO, and the projector has to
re-express presence (omitted vs explicit null) manually.

A normal read/write DTO cannot represent the update contract: an omitted member and an explicit
JSON `null` often collapse into Java `null`, and immutable records may require constructor values
for members absent from the update. Binding an update document directly into a *normal* DTO would
therefore silently lose presence information.

## Decision

Add an opt-in typed PATCH DTO path to the Jackson 2 and Jackson 3 adapters. Each path decodes and
aggregate-validates a JSON:API resource-update document and binds it **directly** into an
application-owned annotated PATCH DTO:

- The PATCH DTO uses the familiar annotations (`@JsonApiResource`, `@JsonApiId`,
  `@JsonApiAttribute`, `@JsonApiRelationship`). There is no `PatchCommand -> DTO` projector and no
  separate normal/base resource DTO is required or inferred — the PATCH DTO is the authoritative
  binding schema.
- Patchable attributes and relationships are declared as `PatchPresence<T>`: a sealed tri-state
  with `Omitted` (member absent from the update), `Present(value)` (supplied non-null), and
  `Present(null)` (supplied explicit JSON `null` or null relationship linkage).
- `PatchPresence<T>` is a Jackson-major-neutral contract in `jsonapi-java-jackson-api`. It
  models only presence and never owns mapping, validation, persistence, or business semantics;
  applications inspect the tri-state and decide how to apply each member.
- Nullable `Optional<T>` is **not** the presence contract. It is a distinct inner-value concern, so
  `PatchPresence<Optional<T>>` remains meaningful: omitted is `Omitted()`, while a supplied `null`
  becomes `Present(Optional.empty())` when that is the inner type's normal conversion result.
- The identifier binds exactly like normal DTO mapping: a valid identifier mapping (explicit
  `@JsonApiId` or the conventional property whose configured Jackson external name is `id`) is
  required, identity comes from resource `id` only (no `lid` fallback), and identity is never a
  patchable member (never wrapped in `PatchPresence`).
- Unannotated ordinary DTO properties do not participate. A supplied document member that does not
  match a role-annotated (or conventional-`id`) property is **rejected** with
  `UNKNOWN_PATCH_MEMBER`. This intentionally diverges from the low-level `PatchCommand` path, which
  silently ignores unknown supplied members to stay a lossless change list; direct DTO binding has
  no lossless intermediate, so rejecting is the safer PATCH default.
- PATCH DTOs are ordinary Jackson beans (records, creators, or setter classes) and construction
  preserves Jackson authority (ADR-004) subject to the documented PATCH-wrapper restrictions:
  creators, deserializers, converters, naming, ignore rules, and configured modules all apply
  through a single `convertValue` over a synthetic property map, except that wrapper-level
  `@JsonDeserialize` / `@JsonSerialize` customization on `PatchPresence<T>` properties is rejected
  (below). Inner-type Jackson customization remains fully supported through normal conversion.
- Construction uses a minimal internal presence marker (`present` boolean + JSON-compatible inner
  value) plus a small contextual `PatchPresence` deserializer registered on the derived binder
  mapper. The marker's wire shape is deterministic and independent of any caller property naming
  strategy: an internal serializer always emits exactly the `present` and `value` member names, and
  the deserializer performs the sole atomic inner-type conversion through the caller-derived
  configuration so inner-type deserializers and modules remain authoritative. The `present` boolean
  is a primitive, so no caller `JsonInclude` configuration (`NON_ABSENT`, `NON_EMPTY`, `NON_NULL`)
  can collapse the tri-state:
  `Omitted()`, `Present(null)`, and `Present(value)` survive. A marker that is not exactly this
  internal shape (unknown or mangled member names, a non-boolean or absent `present`, or a
  non-object value) fails loudly instead of silently reconstructing `Omitted()`.
- Wrapper-level property `@JsonDeserialize` / `@JsonSerialize` on a `PatchPresence<T>` property is
  **rejected** at declaration validation with `INVALID_PATCH_PROPERTY_TYPE`: it would replace the
  internal presence machinery. The check covers every Jackson serialization/deserialization
  customization path surfaced on the effective property metadata — custom `using` serializers and
  deserializers, converters (`converter`), key/content and null customizers, typing and type
  refinement (`as`/`keyAs`/`contentAs`), and the same customizations supplied through mix-ins.
  Inner-type customization (type-level deserializers/converters, modules, naming) remains fully
  supported through normal Jackson conversion.
- The low-level `PatchCommand<T>` path (ADR-012) stays available and unchanged. The direct typed DTO
  path reuses the internal conversion collaborator for identity parsing and relationship-linkage
  conversion, while atomic `PatchPresence<T>` attributes and meta values remain JSON-compatible
  marker values until the contextual deserializer converts the inner type exactly once. This keeps
  configured Jackson authority consistent without converting typed atomic values twice.
- Applications own authorization and application; the library validates, converts, and binds only.

## Consequences

- Applications can consume PATCH bodies directly as annotated DTOs without a projector, while the
  tri-state keeps omitted distinct from explicit null.
- Direct binding is the safer default for unknown members: they fail loudly instead of being
  silently dropped.
- `PatchPresence` lives in the Jackson-major-neutral contracts, and the same typed marker/deserializer
  contract is implemented by both Jackson adapters.
- PATCH DTOs are a separate shape from normal read/write DTOs: one Java type cannot dual-use as
  both (dual-use is unsupported because the shapes are disjoint), and applications may declare
  per-member types that differ from another DTO for the same JSON:API type.
- Records and other immutable PATCH DTOs work because omitted members bind to `Omitted()` rather
  than requiring fabricated defaults.
- The internal marker/deserializer machinery is adapter-internal and pinned to the module's
  Jackson line (3.2.2); the neutral `PatchPresence` contract does not depend on it.
