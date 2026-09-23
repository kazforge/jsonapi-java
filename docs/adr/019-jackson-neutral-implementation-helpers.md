# ADR-019: Jackson-Neutral Implementation Helpers

**Status:** Accepted
**Date:** 2026-09-09

## Context

The two Jackson adapters share small pieces of JSON:API bookkeeping that do not depend on a parser,
mapper, Jackson type model, or serializer. Duplicating that neutral state invites semantic drift, but
sharing native Jackson mechanics would create a lowest-common-denominator abstraction.

## Decision

Keep neutral helper implementations in the existing `jsonapi-java-api` artifact under the
`com.kazforge.jsonapi.internal` namespace and its subpackages. These classes are Java-public
only for cross-artifact cooperation. They are unsupported application API and must not occur in
supported public signatures.

The shared boundary is limited to neutral bookkeeping for wire member classification and pointers,
mapping roles and identifier-meta copies, supplied PATCH markers, effective representation state, and
compound-inclusion identity, order, count, and conflict tracking.

Each adapter continues to own parser traversal, native source-location conversion, mapper
introspection, Jackson type models, property conversion, serializers and deserializers, binders,
module registration, writers, and inclusion traversal/type resolution. No new artifact, public
facade, neutral parser or mapper, or traversal SPI is introduced.

Supported packages remain the neutral `api`, `document`, `mapping`, `patch`, `representation`, and
`diagnostic` namespaces. Architecture tests enforce Jackson-major freedom in the neutral artifact,
prevent internal types from leaking through supported signatures, and keep adapter contracts from
being redeclared.

## Consequences

- Neutral bookkeeping has one implementation and focused tests while native Jackson integration
  remains explicit and independently compiled.
- The existing module dependency direction is unchanged.
- Unsupported Java-public classes are present in the API artifact; consumers must not depend on them.
- Intentional duplication remains for major-specific mechanics, and boundary changes require matching
  architecture-test and duplication-policy updates.

## Partial supersession

The published-but-unsupported `jsonapi-java-mapping` artifact now owns the first extraction from
this shared boundary. Compound-inclusion path validation, traversal, identity, order, count,
conflict, and sparse-fieldset-exemption behavior moved there together with the neutral
per-invocation inclusion state and effective-representation fieldset lookup; the API artifact no
longer carries `com.kazforge.jsonapi.internal.representation`.

That extraction introduces one narrow unsupported capability interface,
`com.kazforge.jsonapi.mapping.internal.InclusionBackend`, which each backend implements over its
native type tokens, mapping definitions, property access, and selective rendering. It is
implementation detail for backend cooperation, not a public facade, consumer SPI, neutral parser or
mapper, or general traversal SPI; supported backend signatures must not expose it. The
no-new-artifact and unchanged-dependency-direction conclusions above therefore no longer apply.

A later extraction moved the neutral mapping-role enum and added backend-neutral per-property
semantic metadata to `jsonapi-java-mapping`: role, logical backend property identity, configured
backend external name, and JSON:API member name, with the adapter-independent role and name
invariants enforced on construction. The API artifact no longer carries
`com.kazforge.jsonapi.internal.mapping.PropertyRole`. Each adapter still owns its write and read
property records, native handles, and type models, and composes the shared semantic value into
them; relationship-meta metadata exists only in matched form.

A further extraction moved backend-neutral basic resource-write orchestration to
`jsonapi-java-mapping`: fieldset validation and filtering, strict versus create-request identity
rules, empty-member omission, attribute and member naming, ordinary domain-object to-one/to-many
identifier construction, and base `ResourceObject` assembly. Each adapter composes that writer
through a second unsupported capability interface,
`com.kazforge.jsonapi.mapping.internal.WriteResourceBackend`, deliberately limited to native
mechanics: mapping lookup, property access, identifier and attribute conversion, and effective
native types. Relationship members are built by an adapter-supplied
`com.kazforge.jsonapi.mapping.internal.BasicRelationshipWriter` phase, so relationship-container
handling, the advanced direct and `RelationshipLinkage` forms, whole-meta conversion, decoration,
and per-relationship enrichment stay in the existing adapter write path for later extractions
rather than moving into a duplicated backend abstraction.

The advanced relationship-value normalization then moved to the same writer, so the duplicated
`extractToOneLinkage`/`extractToManyLinkage` orchestration left both adapters. The shared writer now
owns one outer `Optional` unwrap, to-many `List`/array/`Iterable` materialization, null/empty
linkage states, direct `ResourceIdentifier` and to-one `RelationshipData` pass-through, ordinary
target linkage, `RelationshipLinkage` target recursion, per-occurrence ordering, null-item
skipping, direct-identifier/domain-object mixed-value rejection, and one standardized wrapper edge:
a `RelationshipLinkage` target that maps to explicit-null or collection linkage fails consistently
with `INVALID_IDENTIFIER_META_TARGET` at the occurrence's identifier-meta location, including when
wrapper meta is absent. Type shape derivation stays Jackson-native: each adapter derives a neutral
`com.kazforge.jsonapi.mapping.internal.RelationshipShape` through its own `MappingTypeSupport`, and
the shared writer consults it only lazily — null/empty, direct-identifier, and direct-data branches
never consult the ordinary target token. Two narrow adapter callbacks keep the remaining native
mechanics adapter-owned: target resolution (runtime specialization plus declared element-type
validation) and wrapper identifier-meta enrichment (property-scoped conversion, object-shape
validation, overlay, and their current stable diagnostics). Whole-meta conversion, decoration, and
relationship-level meta stay in the existing adapter write path for the later write-meta
extraction.

A later extraction moved whole-object write-meta semantics and relationship assembly to the same
writer, so the duplicated relationship-member orchestration and meta handling left both adapters.
The neutral write definition now carries the optional resource-meta property and the matched
relationship-meta properties, and the shared writer owns resource, relationship, and identifier meta
construction and attachment, the identifier overlay, relationship-member assembly in declaration
order, and the stable meta diagnostics and resource-relative locations. The adapter-supplied
`BasicRelationshipWriter` phase and the `RelationshipMetaEnricher` callback were removed; the
`WriteResourceBackend` capability interface now supplies the neutral declared
`RelationshipShape`, native target resolution, and the property-scoped whole-meta and declared-type
identifier-meta conversion operations, while the `MemberConversion` result (generalized from the
former attribute-only conversion result) keeps omission distinct from emitted JSON null across
attributes and all three meta locations. Declared meta-target validation, native type
specialization, unresolved-target validation, decoration, and configured conversion remain
adapter-owned. The attribute-conversion result no longer exists under its old name; the neutral
`RelationshipTargetResolver` callback type was removed as target resolution moved into the backend
capability.

A later extraction moved additive resource and relationship link decoration to the same module. The
shared `com.kazforge.jsonapi.mapping.internal.ResourceDecorationWriter` now owns exact
effective-class decorator lookup, decorator failure/null translation, relationship target
classification against the neutral write definition, logical-to-wire name resolution, whole-value
link replacement, fieldset non-resurrection, and reconstruction that preserves the other members the
basic write produced. Each adapter retains only the empty-registry short-circuit, the effective
runtime raw-class resolution, and delegation; the decorator contracts and registry remain in the
neutral API. This supersedes the earlier statements above that decoration stays in the existing
adapter write path.

A later extraction moved backend-neutral basic resource-read orchestration to the same module. The
shared `com.kazforge.jsonapi.mapping.internal.BasicResourceReader` now owns resource-type matching
through the existing `ResourceTypeMatch` authority, strict and independent `id`/`lid` role
selection, wire-member lookup by JSON:API name, the distinction between an absent attribute and a
present JSON null, the distinction between an absent relationship (or absent relationship `data`)
and present linkage, synthetic input keys by backend external name, member-relative diagnostic
locations, and the non-deserializable and identifier-conversion diagnostics. Each adapter supplies
configured wire-identifier parsing and configured relationship-linkage conversion through a third
unsupported capability interface,
`com.kazforge.jsonapi.mapping.internal.ReadResourceBackend`, deliberately limited to a mapped
property's diagnostic raw class, wire-identifier parsing, and relationship-linkage conversion.
Whole-object meta and relationship-meta binding, declared meta-target validation, construction-path
metadata, and the single configured bean construction stay in each adapter's `DomainResourceBinder`,
so those concerns remain adapter-owned for a later extraction.

A later extraction moved backend-neutral advanced relationship and Meta read orchestration to the
same shared reader, so the duplicated relationship-linkage and meta loops left both adapters. The
neutral `com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition` now carries the optional
resource-meta property and the matched relationship-meta properties alongside the identity,
attribute, and relationship properties, and `BasicResourceReader` additionally owns relationship
cardinality validation, null/empty short-circuiting, direct `ResourceIdentifier` copies that
preserve identifier meta and drop additional members, opt-in `RelationshipLinkage` occurrence
orchestration with per-occurrence target and identifier-meta pairing, and resource/relationship meta
presence, bindability, wire locations, and raw-member binding. Each adapter derives a neutral
`com.kazforge.jsonapi.mapping.internal.ReadRelationshipShape` through its own `MappingTypeSupport`,
distinguishing direct versus mapped targets, to-one versus to-many, the ordinary mapper target
token, and wrapped occurrence target and identifier-meta tokens. Shape resolution stays lazy and
happens only after supplied relationship `data` is present and bindable, so an unsupported or
unresolvable target still fails before the shared cardinality and short-circuit checks and no mapper
is invoked for empty linkage. The `ReadResourceBackend` capability interface now carries a native
type token and a property token and is limited to a mapped property's diagnostic raw class,
configured wire-identifier parsing, lazy relationship-shape resolution (native target/type
resolution plus configured-mapper selection), configured linkage-mapper invocation, and declared
identifier-meta conversion. Declared meta-target validation, native `JavaType` specialization,
configured conversion, and the single configured bean construction remain adapter-owned, as does the
adapter-local relationship-linkage code still used by PATCH. This supersedes the earlier statements
above that whole-object meta and relationship-meta binding stay in each adapter.

The remaining helpers listed in the Decision (wire member classification and pointers,
identifier-meta copies, and supplied PATCH markers) stay in the API artifact under
`com.kazforge.jsonapi.internal` until a later extraction moves them. `IdentifierMetaSupport`
continues to live there: its identifier-meta locations and `ResourceIdentifier` copy are shared by
the neutral writer, the neutral reader, and PATCH alike, and the extraction demonstrated no concrete
dependency or ownership problem that would justify relocating it.

A later hardening increment moved the neutral top-level construction-start translation to the shared
read definition. `ReadResourceDefinition#constructionStarts(...)` now maps each bindable read
property's backend external name to its resource-relative JSON:API start location — supplied
`/id` and `/lid`, `/attributes/<jsonApiName>`, `/relationships/<jsonApiName>/data`, `/meta`, and
`/relationships/<targetJsonApiName>/meta` — paired with the same opaque property token the
definition already carries, so the two adapters cannot drift in backend-name to JSON:API-location
translation and each adapter's nested walker needs no second lookup or separately supplied
effective-type token. Identity starts are emitted only when the wire member was supplied, and
non-bindable properties contribute no start. The duplicated adapter-local
`ReadResourceMapping.constructionStartsByJacksonName(...)` implementations were removed; the
write-oriented `ResourceMapping.constructionStartsByJacksonName(...)` and the adapter-local
`MappingConstructionStart` record remain scoped to typed PATCH construction. Effective property
discovery, nested bean-shape walking, effective native types, configured deserialization, native
failure-path extraction, Optional/type unwrapping, and the single final bean construction remain
adapter-owned: each adapter's read mapping now carries the configured bean deserializer's effective
`SettableBeanProperty`, which is the single authority for effective type, creator participation,
injection-only exclusion, and active-view visibility, and each adapter's nested path walker recurses
through those effective properties instead of a serialization-side `BeanPropertyDefinition` primary
type. Jackson 3 ordinary flat reads classify a missing creator input from the effective creator
property names through an explicit `BeanConstruction` seam; the typed PATCH DTO binder keeps its
existing default classifier.

A later extraction moved backend-neutral low-level PATCH command orchestration to the same module.
`com.kazforge.jsonapi.mapping.internal.PatchCommandBinder` now owns resource-type matching, required
`id` identity that never falls back to `lid`, supplied-member lookup by JSON:API name, bindability
enforcement, `PatchChange` construction, `PatchCommand` assembly, and the contract phase order
(resource-type match, backend declared meta-target validation, identity, resource meta, attributes in
wire encounter order, and relationships in wire encounter order with each relationship-meta change
adjacent to its relationship and emitted only beside supplied relationship `data`). Each adapter
projects a dedicated `com.kazforge.jsonapi.mapping.internal.PatchResourceDefinition` from the same
deserialization-resolved inbound mapping that ordinary reads use, so one configured-Jackson authority
owns merged role annotations, effective properties, bindability, generic specialization, class
metadata, active views, and matched relationship-meta resolution; ordinary reads and low-level PATCH
remain separate neutral projections and never resolve competing configured-Jackson models. PATCH
participation is resolved from effective deserialization targets, so setter-only, creator-only, and
write-only properties participate while a supplied mapped property without an effective
deserialization target fails instead of being converted from a serialization accessor. The shared
`com.kazforge.jsonapi.mapping.internal.RelationshipLinkageBinder` now owns relationship cardinality,
null/empty short-circuiting, direct identifier copies, wrapper occurrence orchestration, configured
linkage-mapper callback sequencing, and identifier-meta sequencing for both ordinary reads and
low-level PATCH, so the duplicated adapter-side low-level linkage route left both adapters. Each
adapter reaches its native mechanics through a thin
`com.kazforge.jsonapi.mapping.internal.PatchResourceBackend` boundary limited to declared meta-target
validation against the effective inbound PATCH property types, identity and attribute/meta
conversion, recursive structured binding, final relationship container coercion, and the shared lazy
relationship-shape, linkage-mapper, and identifier-meta operations; adapter-local
`StructuredValueBinder`, property-scoped deserialization, custom-mapper invocation, native target
resolution, and native diagnostics remain adapter-owned. The typed `PatchPresence` DTO path and
recursive structured PATCH are out of scope for this increment. This supersedes the earlier
statements above that the adapter-local relationship-linkage code is still used by PATCH and that
low-level PATCH retains write-oriented `ResourceMapping`.

