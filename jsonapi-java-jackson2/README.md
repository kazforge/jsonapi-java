# jsonapi-java-jackson2

Jackson 2 implementation of the validated JSON:API document codec: validating and writing, plus
token-driven reading of, [JSON:API v1.1](https://jsonapi.org/) documents with deterministic wire
semantics, plus advanced domain-to-resource mapping with compound inclusion, sparse fieldsets, and
additive decoration, and validated flat resource-to-DTO binding.

> This is a Jackson 2 parity artifact. It currently holds the document writer, the validated
> document reader, the advanced write-side resource mapper, and the flat DTO resource binder;
> typed envelopes, presence-aware PATCH, and the Level-1 configured runtime follow in later parity
> stories. The Jackson 3 module ([jsonapi-java-jackson3](../jsonapi-java-jackson3/README.md)) owns
> the full capability set today.

## Packages

| Package                                        | Role                                                                  |
|------------------------------------------------|-----------------------------------------------------------------------|
| `io.github.kazemek.jsonapi.jackson2`           | Public codec factories (`JsonApiJackson2`), validate-then-emit `JsonApiDocumentWriter`, token-driven `JsonApiDocumentReader`, `JsonApiResourceMapper` for domain-to-resource mapping, and `JsonApiResourceBinder` for validated flat resource-to-DTO binding |
| `io.github.kazemek.jsonapi.jackson2.internal`  | Streaming document serializer, wire emission, token-driven wire decoding, the mapping engine, and module registration; not public API |
| `io.github.kazemek.jsonapi.jackson.*`          | Public Jackson-major-neutral API contracts (in `jsonapi-java-jackson-api`): `api`, `document`, `mapping`, `patch`, `representation`, `diagnostic` |

Validation policy, read policy, mapping policy, contexts, provenance values, diagnostics
(`ValidationContext`, `DocumentReadContext`, `RepresentationSelection`, `RepresentationPolicy`,
`MappedDocument`, `IdentifierConverter`, `ResourceDecoratorRegistry`, `RelationshipLinkage`,
`JsonApiValidationException`, `JsonApiDocumentReadException`, `MappingDiagnostic`), and the
opt-in identifier-meta wrapper live in `jsonapi-java-core` and `jsonapi-java-jackson-api` and are
imported from there; this module holds only the Jackson 2-bound writer, reader, resource mapper,
resource binder, and their implementations.

## Minimal usage

```java
import com.fasterxml.jackson.databind.json.JsonMapper;

JsonMapper callerMapper = JsonMapper.builder().build();

JsonApiDocumentWriter writer = JsonApiJackson2.writer(callerMapper);
String json = writer.writeValueAsString(document);

JsonApiDocumentReader reader =
    JsonApiJackson2.reader(callerMapper, DocumentReadContext.resourceDefaults());
JsonApiDocument roundTrip = reader.readValue(json);
```

The writer validates a `JsonApiDocument` against its bound `ValidationContext` before any
generator output starts, so validation failure cannot leave a partially written document. It
offers five output forms for plain documents and the same five for `MappedDocument` values:

```java
String json = writer.writeValueAsString(document);
byte[] bytes = writer.writeValueAsBytes(document);
writer.writeValue(outputStream, document);
writer.writeValue(writer_, document);
writer.writeValue(generator, document);
```

Sinks are caller-owned: an `OutputStream` or `Writer` passed to `writeValue` is not closed — only
the generator created for the call is closed — and output is fully visible without an explicit
flush. A caller-created `JsonGenerator` stays open for the caller to close.

Sparse-fieldset provenance composes into the bound validation policy: writing a `MappedDocument`
composes the mapping's linkage-exemption identities into the bound context before validation, and
callers never translate mapping provenance into a validation context themselves. An empty
exemption set validates exactly like plain document writing, and unrelated full-linkage defects
still fail:

```java
MappedDocument mapped = mapper.toMappedDocument(article, null, fieldsets, policy);
String json = JsonApiJackson2.writer(callerMapper).writeValueAsString(mapped);
```

## Domain-to-resource mapping

```java
JsonMapper callerMapper = JsonMapper.builder().build();
JsonApiResourceMapper mapper = JsonApiJackson2.resourceMapper(callerMapper);

JsonApiDocument doc = mapper.toDocument(someAnnotatedPojo);
String json = JsonApiJackson2.writer(callerMapper).writeValueAsString(doc);
```

The mapper produces core model objects; serialization stays an explicit handoff to the
`JsonApiDocumentWriter`. Annotations assign semantic roles only (`@JsonApiResource`, `@JsonApiId`,
`@JsonApiLocalId`, `@JsonApiAttribute`, `@JsonApiRelationship`, `@JsonApiMeta`,
`@JsonApiRelationshipMeta`); configured Jackson owns property discovery, visibility, external
naming, mix-ins, creators, and value conversion. Unannotated Jackson-visible properties do not
participate, except the conventional identifier whose Jackson external name is `id`.

Key semantics (identical to the Jackson 3 mapper):

- **Independent identity roles:** `@JsonApiId` maps only `ResourceObject.id` and `@JsonApiLocalId`
  maps only `ResourceObject.lid`; neither role falls back to the other. `toMappedCreateDocument`
  maps an identity-less primary with absent `id`/`lid` and leaves that leniency to core
  create-request validation.
- **Linkage-oriented relationships (ADR-018):** every selected ordinary relationship emits present
  `data` — `null` linkage for a null/empty `Optional` to-one, `[]` for an empty to-many.
- **Whole-meta (ADR-015):** `@JsonApiMeta` and `@JsonApiRelationshipMeta(relationship = "...")`
  map Bean / `Map` / `Object` targets (at most one `Optional` wrapper) through the mapped
  property's contextualized Jackson property writer.
- **Identifier meta (ADR-017):** the opt-in `RelationshipLinkage<T, M>` wrapper overlays `meta`
  onto that occurrence's `ResourceIdentifier` while preserving type/id/lid/additional members.
- **Typed generics:** convenience methods infer the root `JavaType` from the runtime class; pass a
  complete `com.fasterxml.jackson.databind.JavaType` for parameterized roots so generic members,
  relationship linkage, and include traversal retain their declared bindings. Distinct
  parameterizations cache independent mappings; each mapper instance owns its cache keyed by
  complete `JavaType`.
- **Optional attributes/meta/relationships:** present `Optional` unwraps on write; empty values
  omit the member or emit null linkage. The derived mapping mapper registers the pinned JDK 8
  datatype module only when the caller's configuration cannot serialize a present `Optional`;
  caller-supplied Optional handling is detected behaviorally and always wins.
- **Compound inclusion (explicit selection/policy):** `toDocument(..., selection, policy)` /
  `toCollectionDocument(..., selection, policy)` traverse opt-in include paths with path
  prevalidation, depth/count limits, deterministic first-discovery order, cycle protection, and
  alias-aware identity deduplication. Relationship mapping alone never requests inclusion.
- **Sparse fieldsets:** applied only by the `toMappedDocument` / `toMappedCollectionDocument`
  overloads, validated by final wire name against `FieldPolicy`; the returned `MappedDocument`
  carries the identities of included resources whose inbound linkage an applied fieldset removed.
  Plain-document overloads reject a non-empty fieldset map.
- **Additive decoration (advanced):** `ResourceDecoratorRegistry` decorators add only
  `ResourceObject.links` and `Relationship.links` for already-mapped relationships, keyed by the
  Jackson logical property name; they never replace linkage/meta or resurrect a fieldset-omitted
  relationship.

## Flat resource-to-DTO binding (validated document model → DTO; bind after `JsonApiDocumentReader`)

```java
JsonApiResourceBinder binder = JsonApiJackson2.resourceBinder(callerMapper);

JsonApiDocument document = JsonApiJackson2.reader(callerMapper, DocumentReadContext.resourceDefaults())
    .readValue(json);
ResourceObject resource = ((DocumentData.SingleResource) document.data()).resource();

FlatArticleDto dto = binder.fromResource(resource, FlatArticleDto.class);

JsonApiDocument collectionDocument = JsonApiJackson2.reader(callerMapper, DocumentReadContext.resourceDefaults())
    .readValue(collectionJson);
List<ResourceObject> resources = ((DocumentData.ResourceCollection) collectionDocument.data()).resources();

List<FlatArticleDto> dtos = binder.fromResources(resources, FlatArticleDto.class);
```

Ordinary flat reads use Jackson's effective **deserialization** property model while retaining
JSON:API role and wire-name metadata from the configured mapper. JSON:API annotations assign
semantic roles; configured Jackson owns discovery, visibility, and the external member name. A
Jackson-visible property participates only through a JSON:API role, except the conventional
identifier whose Jackson external name is `id`. `@JsonApiId` and `@JsonApiLocalId` are independent
identity roles: wire `id` binds only to the id role, wire `lid` binds only to the local-id role,
and neither member ever falls back into the other role's property. A wire `lid` on a type without
a local-id role is ignored, never bound as an identifier. Normal readable/writable, setter-only,
creator-only/constructor-bound, and Jackson write-only properties are supported. A supplied member
mapped to a getter-only, read-only, or otherwise non-deserializable property fails with
`NON_DESERIALIZABLE_PROPERTY` at its JSON:API wire location instead of being silently discarded.

The binder never parses JSON and never reads document `included`; relationships bind from linkage
only (ADR-011). A mapped relationship whose wire object carries no `data` member binds no linkage
(ADR-018): the property stays unbound and the resulting Java value follows configured Jackson
missing-property semantics, so field initializers, creator defaults, and null-handling
customizations remain in effect; explicit `"data": null` binds separately as an explicit null.
`"data": null` on a to-many property fails as invalid linkage cardinality, while `"data": []`
binds an empty collection. That relationship's `meta` still binds through
`@JsonApiRelationshipMeta`. The serialization-oriented `ResourceMapping` remains authoritative
for writes.

Relationship linkage binds `ResourceIdentifier` (plus `Optional`/`List`/`Set`/array shapes)
directly and preserves identifier meta (ADR-017); the opt-in `RelationshipLinkage<T, M>` wrapper
maps each occurrence's `meta` to `M`. Any other relationship target class requires a registered
`RelationshipLinkageMapper` keyed by that class:

```java
RelationshipLinkageMapper authorMapper =
    (linkage, targetType) -> /* convert RelationshipData to the target */;
JsonApiResourceBinder binder =
    JsonApiJackson2.resourceBinder(callerMapper, IdentifierConverter.defaults(), Map.of(Author.class, authorMapper));
```

Cardinality is enforced before a registered mapper is invoked, and explicit null/empty linkage
short-circuits without one. Identifier conversion (`IdentifierConverter.parse`) inverts the wire
identifier before bean construction; the target identity property's configured deserializer still
applies exactly once during whole-bean construction, so coercion, creators, null providers, and
configured modules remain authoritative (ADR-004). The derived binder mapper registers the pinned
JDK 8 datatype module only when the caller's configuration cannot deserialize a present
`Optional`; caller-supplied Optional handling is detected behaviorally and always wins. Jackson 2's
default primitive-null coercion is preserved as configured: permissive callers bind explicit null
to primitive defaults, and `FAIL_ON_NULL_FOR_PRIMITIVES` callers fail that bind at the attribute's
wire location.

## Construction policy

The canonical seam follows ADR-016: a fully configured `JsonMapper` instance, followed by the
capability context and collaborators:

```java
JsonApiJackson2.writer(mapper);                                        // default validation context
JsonApiJackson2.writer(mapper, validationContext);                     // canonical mapper-instance form
JsonApiJackson2.reader(mapper, readContext);                           // canonical mapper-instance form
JsonApiJackson2.resourceMapper(mapper);                                // default identifier conversion
JsonApiJackson2.resourceMapper(mapper, identifierConverter);
JsonApiJackson2.resourceMapper(mapper, decoratorRegistry);
JsonApiJackson2.resourceMapper(mapper, identifierConverter, decoratorRegistry);
JsonApiJackson2.resourceBinder(mapper);                                // default identifier conversion
JsonApiJackson2.resourceBinder(mapper, identifierConverter);
JsonApiJackson2.resourceBinder(mapper, identifierConverter, linkageMappers);
```

The writer derives its codec mapper via `rebuild()` and registers only the internal JSON:API
document module; the reader uses the supplied mapper directly for token-driven parsing; the
resource mapper derives an isolated mapping mapper via `rebuild()` and registers only the internal
meta-binding module plus, when the caller lacks Optional serialization support, the pinned JDK 8
datatype module; the resource binder derives an isolated binding mapper the same way and adds that
fallback only when the caller lacks Optional deserialization support. No construction path mutates
the caller's mapper, and derived mappers are not public.
`JsonMapper.Builder` overloads are intentionally not part of the API. Jackson 2's checked
`JsonProcessingException` mechanics propagate from emission methods unchanged; every reader
overload declares checked `IOException`, Jackson parse failures surface as payload-safe
`JsonApiDocumentReadException` (`MALFORMED_JSON`), and unrelated source I/O propagates unchanged.
Core validation failures stay unchecked `JsonApiValidationException` on writes and become
`JsonApiDocumentReadException` with `ValidationRuleCode` plus JSON Pointer-like path on reads.
Mapping failures throw `JsonApiMappingException` with `MappingDiagnostic` values per the
mapping-location contract.

## Wire semantics

- Deterministic member order; absence, explicit JSON `null`, and present-empty remain distinct
  wire states; sealed model variants (`NullData`, `NullLinkage`, string vs object links) keep
  their wire shapes.
- Open values (attributes, meta, additional members, link values) accept strings, booleans,
  numbers, lists, maps, and `null`; anything else fails with `IllegalArgumentException`.
- The reader decodes token-driven wire JSON through public core constructors under an explicit
  `DocumentReadContext` (aggregate `ValidationContext` plus explicit `PrimaryDataKind`), then runs
  aggregate validation before returning. Ambiguous `{"type","id"}` and `[]` primary data never
  guess: the bound `PrimaryDataKind` selects resource versus identifier decoding. Whole-input
  overloads (`String`, `byte[]`, `InputStream`) require end-of-input after the document, while the
  caller-parser overload consumes exactly one root value so sequential documents may follow.
  Parsers created by convenience overloads are closed; caller-owned streams and parsers stay open.
- Writer output and corpus documents are cross-checked against the shared passive JSON/schema
  corpus in adapter-owned tests; reader expectations live here, not in shared fixtures.
- Mapped attributes and meta write through their fully contextualized Jackson property writers, so
  configured serializers, null serializers, inclusion, naming, mix-ins, and runtime subtype
  behavior remain authoritative at the property boundary. JSON:API remains authoritative for
  identifier wire strings and relationship linkage.

## Non-goals

Typed envelopes, presence-aware PATCH binding, and the Level-1 configured runtime are later
Jackson 2 parity stories. HTTP `fields[TYPE]` parsing, field authorization, domain graph
hydration, persistence lookup, and command application remain application/adapter responsibilities.
Both majors share the neutral contracts of
[jsonapi-java-jackson-api](../jsonapi-java-jackson-api/README.md) per ADR-007.

## Further reading

- [Architecture overview](../docs/architecture.md)
- [Conformance checklist](../docs/conformance.md)
- [ADR-005 — Domain mapping and inclusion](../docs/adr/005-domain-mapping-and-inclusion.md)
- [ADR-016 — Mapper-instance construction for Jackson adapters](../docs/adr/016-jackson-adapter-construction.md)
- [ADR-010 — Architectural tests](../docs/adr/010-architectural-tests.md)
- [Canonical fixtures](../jsonapi-java-jackson-api/src/testFixtures/resources/jsonapi/corpus/1.1/README.md)
- [Jackson API module](../jsonapi-java-jackson-api/README.md)
- [Jackson 3 module](../jsonapi-java-jackson3/README.md)
- [Root agent workflow](../AGENTS.md)

## For contributors / agents

- **Validate then write / read then validate:** `JsonApiDocumentWriter` and
  `JsonApiDocumentReader` are the sole public codec paths. Failures preserve stable diagnostics
  (`ValidationRuleCode` + JSON Pointer-like path; reads also carry `CodecFailureCategory` and safe
  source location). Emission failures propagate Jackson 2 checked `IOException` mechanics rather
  than a new exception family, and every reader overload declares `IOException` while Jackson parse
  failures become payload-safe `MALFORMED_JSON` values. Do not expose the codec mapper publicly.
- **Map then write:** `JsonApiResourceMapper` produces core model objects; feed them to a writer
  for serialization. Mapping uses Jackson's logical property model and caches `ResourceMapping` by
  complete declared `JavaType` on the derived mapper instance (no cross-mapper cache, no partial
  configuration hash). Ordinary concrete roots infer that type; direct parameterized roots use the
  `JavaType` overloads. Configured Jackson is the single authority for class-level resource
  metadata: `@JsonApiResource` is read through mapper introspection via direct-class annotation
  lookup, so class-level mix-ins provide or override it without inheriting from supertypes or
  interfaces. Mapping diagnostics use `MappingDiagnostic` + domain class rather than core
  validation codes.
- **Local render versus include traversal:** each local member render reads its accessor once and
  supplies that already-read value to property-scoped serialization. Include traversal retains its
  separate on-path relationship read; fieldset-excluded and off-path properties remain unread.
  Never introduce a value-snapshot architecture that rereads or replays accessors.
- **Property-scoped serialization:** mapped attributes and meta go through raw-value-capable
  copies of Jackson's bean property writers (registered by an internal module), preserving
  suppression, contextual/null/type serializers, and omission-vs-emitted-null. Custom
  `BeanPropertyWriter` replacements have no raw-value contract and are rejected rather than
  silently rereading the accessor.
- **Mapping-location contract:** every `JsonApiMappingException` carries an optional location that
  is either absent (`null`) or a valid RFC 6901 JSON Pointer built through `MappingLocation`,
  whose segments are individually escaped. Producers mapping one resource emit resource-relative
  pointers over JSON:API member names (`/id`, `/lid`, `/attributes/<wire-name>`,
  `/relationships/<wire-name>/data`, `/meta`, `/relationships/<wire-name>/meta`,
  `/relationships/<wire-name>/data[/index]/meta`). Failures without a meaningful member coordinate
  (missing annotations, invalid type names, include-path and fieldset specification errors) carry
  an absent location; the identifying names stay in the message.
- **Mapper isolation:** factories accept configured `JsonMapper` instances, never builders, and
  do not mutate them. Close only generators created by convenience overloads; leave caller-owned
  streams/writers/generators open.
- **Optional support fallback:** the derived mapping mapper detects caller Optional serialization
  support behaviorally; the derived binder mapper detects Optional deserialization support the
  same way. Each registers the pinned JDK 8 datatype module only when its probe fails. Preserve
  caller-supplied Optional handling; never register the fallback unconditionally.
- **Validate then bind:** `JsonApiResourceBinder` binds already-validated `ResourceObject` values
  to flat DTOs; it never parses JSON, never reads document `included`, and assembles no domain
  graph. `fromResource`/`fromResources` validate `type` against the target's configured
  `@JsonApiResource` metadata (mix-ins honored) and report `MappingDiagnostic` plus a
  resource-relative pointer per the mapping-location contract above. Bean-construction failures
  translate their Jackson failure path into the mapped member's wire pointer (deeply, through
  resolved shape metadata); unmappable paths carry no location. Missing members are omitted;
  explicit JSON `null` binds null. Read bindability follows Jackson's deserialization metadata
  rather than the serialization accessor: supplied getter-only or otherwise non-deserializable
  mapped members fail with `NON_DESERIALIZABLE_PROPERTY`. Creator/coercion failures classify
  structurally (instantiation rejection versus missing creator input named by the failure path and
  absent from the synthetic input), never from Jackson exception message text. Bind failures throw
  `JsonApiMappingException`, never `JsonApiDocumentReadException`.
- **Architectural tests:** `Jackson2DependencyRulesSpec` allows JDK, JSpecify, core public
  packages, annotations, the common contracts package, module-owned types, and
  `com.fasterxml.jackson..`; bans `core.internal` and Jackson 3 (`tools.jackson..`) in
  production sources, and asserts no moved common-contract type is re-declared here (ADR-010).
- **Tests:** Spock specs under `src/test/groovy/` are boring and explicit: setup, invoke the
  production API, assert directly. Shared test fixtures provide passive corpus/schema resources
  and major-neutral application-shaped DTOs via `jsonapi-java-jackson-api` test fixtures; writer
  and mapper expectations belong in adapter tests. Do not introduce shared test orchestration,
  scenario registries, or assertion frameworks. Small duplication between the Jackson 2 and
  Jackson 3 test suites is acceptable. Keep Jackson-major-specific fixture shapes in small
  `*Fixtures.java` containers next to the owning spec.
- **Nullness:** Production packages are `@NullMarked` (JSpecify only). Use `@Nullable` for
  absence and intentionally null map values. Do not import `core.internal`.
