# jsonapi-java-mapping

Internal cross-artifact mapping implementation namespace for backend runtimes. This module is
published on the unified release train so backend runtime implementations can consume it, but it
is not supported consumer API.

The [`com.kazforge.jsonapi.mapping.internal`](src/main/java/com/kazforge/jsonapi/mapping/internal/package-info.java)
package owns backend-neutral semantics for mapped resources:

- **Compound inclusion.** Include-path validation, breadth-first traversal of intermediate and
  nested resources, identity aliasing, first-encounter order, included deduplication and conflict
  checks, traversal limits, and sparse-fieldset linkage exemptions, plus the neutral
  per-invocation inclusion state and effective-representation fieldset lookup used by selective
  rendering and traversal alike.
- **Basic resource write orchestration.** Fieldset validation and filtering, strict versus
  create-request identity rules, empty-member omission, attribute and member naming, ordinary
  domain-object to-one/to-many identifier construction, advanced relationship-value normalization
  (direct identifiers, direct linkage data, `RelationshipLinkage` wrapper occurrence handling,
  to-many container materialization, null-item skipping, and mixed-value rejection), matched
  relationship-member assembly, resource/relationship/identifier meta construction and attachment,
  identifier-meta overlay, and base `ResourceObject` assembly. Declared meta-target validation and
  configured conversion stay with each backend and are applied around this writer.
- **Basic and advanced resource read orchestration.** Resource-type matching through the shared
  type-match authority, strict and independent `id`/`lid` role selection, wire-member lookup by
  JSON:API name, the distinction between an absent attribute and a present JSON null, the
  distinction between an absent relationship (or absent relationship `data`) and present linkage,
  synthetic input keys by backend external name, member-relative diagnostic locations, and the
  non-deserializable and identifier-conversion diagnostics. Relationship cardinality validation,
  null/empty short-circuiting, direct `ResourceIdentifier` copies that preserve identifier meta and
  drop additional members, opt-in `RelationshipLinkage` occurrence orchestration with
  per-occurrence target and identifier-meta pairing, and resource/relationship meta presence and
  raw-member binding. Each backend supplies configured wire-identifier parsing, lazy
  relationship-shape resolution and configured linkage-mapper invocation, and declared
  identifier-meta conversion through a thin `ReadResourceBackend` boundary; final bean
  construction remains adapter-owned. The neutral read definition also owns the top-level
  construction-start translation: each bindable member's backend external name maps to its
  resource-relative JSON:API start location, paired with the same opaque property token, so the
  adapters share that translation while nested shape walking, effective native types, configured
  deserialization, and native failure-path extraction stay backend-owned.
- **Typed-envelope document binding.** Eager registry coherence over every registration,
  primary-data dispatch (absent, explicit null, single and collection resources, identifier
  pass-through), included-resource binding with wire order, id/lid aliasing, and duplicate-identity
  rejection, document-prefix composition of binder failures, and `/meta` conversion-failure
  translation. Each backend supplies native target-type construction from the registered `Type`,
  configured resource-type name resolution, the raw class used in diagnostics, and per-resource
  binding through a thin `TypedEnvelopeBinder.Backend` boundary; native per-resource binding and
  meta conversion stay adapter-owned.
- **Low-level PATCH command orchestration.** Resource-type matching, required `id` identity that
  never falls back to `lid`, supplied-member lookup by JSON:API name, effective-deserialization
  bindability enforcement, `PatchChange` construction, and `PatchCommand` assembly. The phase order
  is part of the contract: resource-type match, backend declared meta-target validation, identity,
  resource meta, attributes in wire encounter order, and relationships in wire encounter order with
  each relationship-meta change adjacent to its relationship and emitted only beside supplied
  relationship `data`. Whole linkage replacement, cardinality, direct `ResourceIdentifier` copies,
  wrapper occurrence orchestration, and identifier-meta sequencing are shared with ordinary reads
  through the neutral linkage binder. Each backend supplies declared meta-target validation against
  the effective inbound PATCH property types, identity and atomic attribute/meta conversion, final
  relationship target/container coercion, lazy relationship-shape
  resolution, configured linkage-mapper invocation, and identifier-meta conversion through a thin
  `PatchResourceBackend` boundary; configured structured-shape introspection, caching, and native
  atomic conversion are reached through the separate `StructuredShapeBackend` bridge, while
  construction-path translation stays adapter-owned over that resolved shape metadata. Ordinary
  reads and low-level PATCH are separate projections of
  one adapter-resolved deserialization mapping; they never resolve competing configured-Jackson
  models.
- **Recursive structured-value and typed PATCH DTO orchestration.** `StructuredPatchBinder` owns
  typed marker-tree assembly, low-level supplied-only `StructuredPatch`/`StructuredMember`
  assembly, present/omitted/explicit-null/empty-object distinctions, strict typed versus skip
  low-level unknown-member policy, pointer accumulation, and the typed-only nature of
  presence-aware shapes, over an adapter-supplied `StructuredShapeBackend` that supplies resolved
  shape facts, native type queries, and atomic conversion; configured shape introspection and
  caching, wrapper-customization detection, property-scoped conversion, and construction-path
  translation stay adapter-owned. `TypedPatchBinder` owns the typed `PatchPresence<T>` DTO contract
  phase order (resource-type match, complete declaration preflight, required identity, attributes,
  relationships, resource meta, relationship meta, then native construction), synthetic
  `PresenceMarker` assembly, strict supplied unknown-member handling, and meta/data gating, over an
  adapter-supplied `TypedPatchBackend` limited to declared relationship-linkage identifier-meta
  validation, wire-identifier parsing, typed relationship conversion through the shared
  relationship-linkage binder, and the single native construction with identifier-failure
  reclassification. The typed DTO projection remains the
  adapter's serialization-oriented `ResourceMapping`; low-level PATCH keeps its
  deserialization-oriented projection.
- **Additive link decoration.** Exact decorator lookup by effective runtime raw class, decorator
  failure/null translation, relationship target classification and logical-to-wire name resolution,
  whole-value resource and relationship link replacement, fieldset non-resurrection, and
  preservation of every other member the basic write produced. The adapter resolves the effective
  runtime raw class at its own edge and supplies the configured registry; the decorator contracts
  and registry remain neutral API.
- **Mapping roles, naming metadata, and definition invariants.** The backend-neutral mapping roles
  and the per-property semantic metadata that adapters compose into their own write and read
  mapping records: role, logical backend property identity, configured backend external name, and
  JSON:API member name. The adapter-independent role/name invariants are enforced when that value is
  constructed, so identifier roles always name `id`/`lid`, resource meta always names `meta`, and
  attributes and relationships use their backend external name. Relationship-meta metadata is valid
  only in matched form, carrying the target relationship's JSON:API name; an unresolved annotation
  target stays resolver-local. `MappingDefinitionInvariants` owns the resource-level definition
  rules both adapters apply after role and wire-name harvesting: class-level resource type name
  validity, per-property JSON:API member-name validity, relationship-meta target matching by logical
  identity under the target's wire name, and the resource-level role rules (a single `id` and
  local-id role, duplicate attribute and relationship names, attribute/relationship collisions, and
  a single resource meta). Diagnostic codes, resource classes, locations, messages, and evaluation
  order are shared semantics; configured property discovery, role-annotation harvesting,
  merged-Jackson-name rejection, effective-property resolution, and the remaining adapter-local
  declaration checks stay backend-owned.
- **Shared neutral mapping helpers.** Identifier-meta locations and linkage
  `ResourceIdentifier` copies, resource-type match enforcement for binders, the Level-1
  primary-data shape policy (resource and relationship-linkage document shape checks, shared
  mismatch descriptions, and data-only linkage-document assembly), and the synthetic typed-PATCH
  presence-marker carrier. Both backends and the shared orchestrators consume these Java-public
  types; they are implementation detail for backend cooperation, not supported consumer API.

Native type and property models, introspection, mapping lookup, property access, native container
type-shape derivation, configured conversion (including whole-meta and declared-type identifier-meta
serialization), identifier conversion, declared meta-target validation, effective-type resolution,
and selective rendering stay in each backend. The compound-inclusion engine reaches native mechanics
through the `InclusionBackend` capability boundary; the resource writer reaches them through a thin
`WriteResourceBackend` boundary limited to mapping lookup, property access, identifier and attribute
conversion, whole-meta and declared identifier-meta conversion, native relationship-shape derivation
and target resolution, and native type specialization. The shared decoration phase receives the
adapter's already-resolved effective runtime raw class and the configured registry. Advanced
relationship normalization runs through a neutral declared `RelationshipShape` that keeps native type
specialization and unresolved-target validation adapter-owned while the shared writer owns the
neutral meta semantics and diagnostics. The resource reader reaches its native mechanics through a
thin `ReadResourceBackend` boundary limited to a mapped property's diagnostic raw class, configured
wire-identifier parsing, lazy relationship-shape resolution (native target/type resolution and
mapper selection), configured linkage-mapper invocation, and declared identifier-meta conversion,
while resource-type matching, identity-role selection, member presence and order, synthetic-input
assembly, cardinality validation, direct-identifier copying, wrapper occurrence orchestration,
resource/relationship meta binding, and the shared member diagnostics stay in the reader. Typed
envelope document binding reaches native target-type construction, configured resource-type name
resolution, and per-resource binding through a `TypedEnvelopeBinder.Backend` bridge, while registry
coherence, primary-data dispatch, included identity handling, binder-failure composition, and
`/meta` conversion-failure translation stay in the binder. The neutral relationship shape used by
the reader and the low-level PATCH binder keeps native
specialization and configured conversion adapter-owned. The low-level PATCH binder reaches its
native mechanics through a thin `PatchResourceBackend` boundary limited to declared meta-target
validation, identity and attribute/meta conversion, final relationship container coercion, and the
same lazy relationship-shape, linkage-mapper, and identifier-meta operations as the reader. The
recursive structured engine reaches native shape discovery and atomic conversion through a
`StructuredShapeBackend` bridge, and the typed PATCH DTO orchestrator reaches identity parsing,
relationship conversion, and the single bean construction through a `TypedPatchBackend` bridge,
leaving configured Jackson introspection, wrapper-customization detection, property-scoped
conversion, and construction-path translation adapter-owned. Adapter
write and read property records remain backend-owned; only the semantic value they compose is
shared. This module imports no Jackson-major or concrete-adapter package.

## Boundary

- Depends on `jsonapi-java-api` (which depends on `jsonapi-java-core`); never on a backend or a
  backend-native library.
- Backend supported public signatures must not expose this package.
- Application code must not depend on this package.

See the [architecture overview](../docs/architecture.md),
[ADR-005](../docs/adr/005-domain-mapping-and-inclusion.md),
[ADR-007](../docs/adr/007-module-boundaries.md), and
[ADR-022](../docs/adr/022-responsibility-based-mapping-and-native-wire-codecs.md).
