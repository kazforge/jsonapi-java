# jsonapi-java-jackson3

Jackson 3 implementation of the major-neutral Level-1 JSON:API application contract, plus the
advanced capability codecs it is built on: validating, writing, and reading
[JSON:API v1.1](https://jsonapi.org/) documents, and mapping annotated domain types to resource
objects.

> Ordinary application code should start with the Level-1 configured runtime below. The
> major-neutral contract lives in `jsonapi-java-jackson-api`
> ([ADR-019](../docs/adr/019-level-one-application-api-contract.md)); the capability
> factories after it are advanced mechanism/control seams that stay public and unchanged.

## Packages

| Package                                        | Role                                                                  |
|------------------------------------------------|-----------------------------------------------------------------------|
| `io.github.kazemek.jsonapi.jackson3`           | Public Level-1 configured runtime (`Jackson3JsonApi`), writer/reader/mapper/binder/PATCH factories, and validate-then-codec entry points |
| `io.github.kazemek.jsonapi.jackson3.internal`  | Streaming serializers/decoders, mapping engine, module registration; not public API |
| `io.github.kazemek.jsonapi.jackson.*`          | Public Jackson-major-neutral API contracts (in `jsonapi-java-jackson-api`): `api`, `document`, `mapping`, `patch`, `representation`, `diagnostic` |

Codec and mapping policy, contexts, diagnostics, domain envelope values, and presence-aware update
commands (`DocumentReadContext`, `RepresentationSelection`, `RepresentationPolicy`, `IncludePath`,
`IncludePolicy`, `FieldPolicy`, `MappedDocument`, `IdentifierConverter`, `DomainData`, `IncludedResources`,
`PatchCommand`, `PatchChange`, and the failure types) live in the Jackson-major-neutral API
packages `io.github.kazemek.jsonapi.jackson.api`, `document`, `mapping`, `patch`, `representation`,
and `diagnostic` and are imported from `jsonapi-java-jackson-api`; this module holds only the
Jackson 3-bound runtime, factories, readers, writers, and binders.

## Level-1 application runtime

```java
JsonMapper callerMapper = JsonMapper.builder().build();

Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(callerMapper);

String json = jsonApi.resources().writeOne(article);
ArticleDto readBack = jsonApi.resources().readOne(json, ArticleDto.class);

List<ArticleDto> all = jsonApi.resources().readMany(collectionJson, ArticleDto.class);

ResourceIdentifier author = jsonApi.relationships().readToOne(linkageJson);
String linkageJson = jsonApi.relationships().writeToOne(author);

ArticlePatch patch = jsonApi.patches().readPatch(patchJson, ArticlePatch.class);
PatchCommand<ArticleDto> command = jsonApi.patches().readCommand(patchJson, ArticleDto.class);
```

The runtime coordinates mapping, configured decoration, validation, and writing internally, so
callers never orchestrate those phases. Reads are strict and homogeneous: `readOne` requires
single-resource primary data and `readMany` requires a collection, and incompatible shapes fail
with mapping diagnostics rather than coercing. Document-level `links`, `meta`, and `jsonapi`
travel through `ResourceWriteOptions`, and client-side create/update authoring selects core
create/update validation without raw-document choreography:

```java
Jackson3JsonApi jsonApi =
    JsonApiJackson3.builder(callerMapper)
        .identifierConverter(identifierConverter)
        .representationPolicy(policy)
        .decorators(decorators)
        .build();

String createJson = jsonApi.resources().writeCreateDocument(draft);
String updateJson =
    jsonApi.resources().writeUpdateDocument(article, new EndpointIdentity("articles", "1"));
```

Only coherent application-lifetime configuration belongs on the builder; representation selection,
document envelope, and expected update identity stay per-operation arguments. The builder selects
the same documented defaults as the capability factories when a setting is omitted, and there is
no base validation-context setting: response, create, update, and linkage operations each select
their usage internally.

## Advanced capability APIs

Document codec:

```java
JsonMapper callerMapper = JsonMapper.builder().build();

JsonApiDocumentWriter writer = JsonApiJackson3.writer(callerMapper);
String json = writer.writeValueAsString(document);

JsonApiDocumentReader reader =
    JsonApiJackson3.reader(callerMapper, DocumentReadContext.resourceDefaults());
JsonApiDocument roundTrip = reader.readValue(json);
```

## Construction policy

The canonical construction seam for every Jackson 3 capability starts with a fully configured
`JsonMapper`, followed by the capability-specific context or policy and any collaborators that
cannot be derived safely. The canonical forms are:

```java
JsonApiJackson3.builder(mapper)
    .identifierConverter(identifierConverter)
    .linkageMappers(linkageMappers)
    .representationPolicy(representationPolicy)
    .decorators(decoratorRegistry)
    .build();
JsonApiJackson3.writer(mapper, validationContext);
JsonApiJackson3.reader(mapper, readContext);
JsonApiJackson3.resourceMapper(mapper, identifierConverter);
JsonApiJackson3.resourceMapper(mapper, identifierConverter, decoratorRegistry);
JsonApiJackson3.resourceBinder(mapper, identifierConverter, linkageMappers);
JsonApiJackson3.domainDocumentReader(mapper, readContext, registry, identifierConverter, linkageMappers);
JsonApiJackson3.patchCommandReader(mapper, validationContext, identifierConverter, linkageMappers);
JsonApiJackson3.patchDtoReader(mapper, validationContext, identifierConverter, linkageMappers);
```

`ResourceDecoratorRegistry` is an immutable, application-owned registry of
`ResourceDecorator<T>` instances keyed by domain raw class. Decoration adds only
`ResourceObject.links` and `Relationship.links` for already-mapped relationships; it never replaces
type, id, attributes, linkage, or meta, never affects inclusion, and never resurrects a
fieldset-omitted relationship. Relationship decoration is keyed by the Jackson logical property name
(e.g. `comments`), and the mapper follows the configured-Jackson external name (e.g.
`@JsonProperty("article-comments")`) automatically.

Shorter factory forms remain meaningful conveniences: they select the documented default validation
policy, identifier converter, and empty linkage-mapper set, then delegate to these
mapper-instance forms. `JsonMapper.Builder` overloads are deliberately not part of the API: a
builder that only calls `build()` adds no construction semantics, and Jackson 2 will follow this
semantic capability surface rather than reproduce an overload matrix. Callers retain authority over
mapper modules, mix-ins, naming, serializers, deserializers, visibility, and property behavior.
Future Spring integration can therefore pass its configured mapper, capability context, and required
collaborators directly without knowing about facade conveniences or internal mapper derivation.

Domain-to-resource mapping (map → write):

```java
JsonMapper callerMapper = JsonMapper.builder().build();
JsonApiResourceMapper mapper = JsonApiJackson3.resourceMapper(callerMapper);

JsonApiDocument doc = mapper.toDocument(someAnnotatedPojo);
String json = JsonApiJackson3.writer(callerMapper).writeValueAsString(doc);
```

Identity roles are first-class and independent: `@JsonApiId` maps only `ResourceObject.id`, and
`@JsonApiLocalId` maps only `ResourceObject.lid`. A domain type may carry either role or both; write
mapping never promotes a local identifier to `id`, flat reads never bind `lid` into an id property,
and ambiguous declarations (duplicate roles, or one property claiming both) fail with stable
`MappingDiagnostic`s. A lid-only mapped resource represents a create/local-identifier state; the
document writer's validation context — not mapping — decides whether that state is legal for the
document being produced. The local identifier is a JSON:API protocol concept, not an application
persistence or transient-entity heuristic.

Ordinary relationships are linkage-oriented (ADR-018): a selected mapped relationship always emits a
`data` member — a null or `Optional.empty()` to-one emits `"data": null`, and an empty to-many emits
`"data": []`. A relationship whose `data` member is absent (links-only or meta-only) is a
document-level construction (`Relationship.linkOnly` / `metaOnly` and the document codec), not an
ordinary mapped-property state; decoration likewise never creates one.

Resource-link decoration (advanced):

```java
Links articleLinks = Links.ofLinks(Map.of("self", new Link.StringLink("https://example.test/articles/1")));
ResourceDecorator<Article> articleDecorator =
    article ->
        ResourceDecoration.builder()
            .links(articleLinks)
            .relationship("comments", RelationshipDecoration.links(commentLinks))
            .build();

ResourceDecoratorRegistry decorators =
    ResourceDecoratorRegistry.builder().register(Article.class, articleDecorator).build();
JsonApiResourceMapper decorated = JsonApiJackson3.resourceMapper(callerMapper, decorators);
```

Decoration is additive and keyed by the logical property name (`comments`), not the wire name;
`@JsonProperty("article-comments")` renames the relationship on the wire automatically. Decoration
applies to both primary and `included` resources and never resurrects a fieldset-omitted relationship.
`DocumentEnvelope.links` remains the separate document-level link surface.

The convenience route derives the root type from the concrete runtime class. When the declared
root is parameterized directly, pass the complete Jackson type explicitly so generic attributes,
relationships, and include traversal retain their bindings:

```java
JavaType containerType =
    callerMapper.getTypeFactory().constructParametricType(Container.class, Thing.class);
JsonApiDocument typed = mapper.toDocument(container, containerType, null);
```

`Container<Thing>` and `Container<OtherThing>` therefore have distinct mapping metadata. A concrete
subclass such as `ThingContainer extends Container<Thing>` can use the convenience route when
Jackson resolves its bound superclass type. A direct runtime `Container` instance cannot recover
`Thing` from `resource.getClass()`; an unparameterized write fails deterministically when a mapped
member needs that information rather than guessing from runtime values.

Flat resource-to-DTO binding (validated document model → DTO; bind after `JsonApiDocumentReader`):

```java
JsonApiResourceBinder binder = JsonApiJackson3.resourceBinder(callerMapper);

JsonApiDocument document = JsonApiJackson3.reader(callerMapper, DocumentReadContext.resourceDefaults())
    .readValue(json);
ResourceObject resource = ((DocumentData.SingleResource) document.data()).resource();

FlatArticleDto dto = binder.fromResource(resource, FlatArticleDto.class);

JsonApiDocument collectionDocument = JsonApiJackson3.reader(callerMapper, DocumentReadContext.resourceDefaults())
    .readValue(collectionJson);
List<ResourceObject> resources = ((DocumentData.ResourceCollection) collectionDocument.data()).resources();

List<FlatArticleDto> dtos = binder.fromResources(resources, FlatArticleDto.class);
```

Ordinary flat reads use Jackson's effective **deserialization** property model while retaining
JSON:API role and wire-name metadata from the configured mapper. JSON:API annotations assign
semantic roles; configured Jackson owns discovery, visibility, and the external member name. A
Jackson-visible property participates only through a JSON:API role, except the conventional
identifier whose Jackson external name is `id`. `@JsonApiId` and `@JsonApiLocalId` are independent
identity roles: wire `id` binds only to the id role, wire `lid` binds only to the local-id role, and
neither member ever falls back into the other role's property. A wire `lid` on a type without a
local-id role is ignored, never bound as an identifier. Normal readable/writable,
setter-only, creator-only/constructor-bound, and Jackson write-only properties are supported. A
supplied member mapped to a getter-only, read-only, or otherwise non-deserializable property fails
with `NON_DESERIALIZABLE_PROPERTY` at its JSON:API wire location instead of being silently
discarded. A mapped relationship whose wire object carries no `data` member binds no linkage
(ADR-018): the property stays unbound and the resulting Java value follows configured Jackson
missing-property semantics, so field initializers, creator defaults, and null-handling
customizations remain in effect; explicit `"data": null` binds separately as an explicit null.
`"data": null` on a to-many property fails as invalid linkage cardinality, while `"data": []` binds
an empty collection. That relationship's `meta` still binds through `@JsonApiRelationshipMeta`. The
same rule applies to
primary and included resources bound through the typed domain envelope; the serialization-oriented
`ResourceMapping` remains authoritative for writes.

Compound inclusion (explicit selection and policy; relationship mapping alone never includes):

```java
RepresentationSelection selection =
    RepresentationSelection.builder().include(IncludePath.of("comments.author")).build();
RepresentationPolicy policy =
    RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll());

JsonApiDocument compound = mapper.toDocument(article, null, selection, policy);

// The same inputs are available on the declared-type overload:
JsonApiDocument typedCompound = mapper.toDocument(container, containerType, null, selection, policy);
```

Sparse fieldsets (same selection/policy boundary; only via `MappedDocument` overloads):

```java
RepresentationSelection fieldsets =
    RepresentationSelection.builder()
        .include(IncludePath.of("author"))
        .fields("articles", "title")
        .build();
RepresentationPolicy fieldsetPolicy =
    RepresentationPolicy.defaults()
        .withIncludePolicy(IncludePolicy.allowAll())
        .withFieldPolicy(FieldPolicy.allowAll());

MappedDocument mapped = mapper.toMappedDocument(article, null, fieldsets, fieldsetPolicy);

// The writer composes its validation policy with the mapping's linkage exemptions;
// callers never translate provenance into a ValidationContext themselves.
String json = JsonApiJackson3.writer(callerMapper).writeValueAsString(mapped);
```

Bare resource (inspect or compose a document yourself; not a top-level wire payload):

```java
ResourceObject resource = mapper.toResource(someAnnotatedPojo);
```

Collection primary data (also a `JsonApiDocument`; feed it to the same writer):

```java
JsonApiDocument collDoc = mapper.toCollectionDocument(allPojos);
```

Typed domain envelope (validated JSON:API JSON → flat DTOs; no `JsonApiDocument` in routine
signatures):

```java
ResourceTypeRegistry registry =
    ResourceTypeRegistry.builder(callerMapper)
        .register(FlatArticleDto.class)
        .register(AuthorDto.class)
        .build();

JsonApiDomainDocumentReader domainReader =
    JsonApiJackson3.domainDocumentReader(
        callerMapper, DocumentReadContext.resourceDefaults(), registry);

JsonApiDomainDocument envelope = domainReader.readValue(json);
FlatArticleDto article = (FlatArticleDto) ((DomainData.SingleResource) envelope.data()).resource();
Optional<Object> includedAuthor =
    envelope.included() == null
        ? Optional.empty()
        : envelope.included().find(ResourceIdentity.ofId("people", "9"));
```

Presence-aware PATCH (validated update document → immutable command of supplied changes only):

```java
JsonApiPatchCommandReader commandReader = JsonApiJackson3.patchCommandReader(callerMapper);

PatchCommand<FlatArticleDto> command =
    commandReader.readValue(updateJson, FlatArticleDto.class);
Object identity = command.identity();
List<PatchChange> changes = command.changes();
```

Direct typed PATCH DTO binding (validated update document → annotated PATCH DTO with
`PatchPresence<T>` members; no projector, no separate normal DTO):

```java
@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author) {}

JsonApiPatchDtoReader patchDtoReader = JsonApiJackson3.patchDtoReader(callerMapper);

ArticlePatch patch = patchDtoReader.readValue(updateJson, ArticlePatch.class);
if (!patch.title().isOmitted()) {
  // patch.title() is Present("new title") or Present(null) for explicit JSON null
}
```

`included` resources bind independently through the registry, stay wire-ordered, and are never
injected into relationship properties; identifier primary data passes through as core
`ResourceIdentifier` values. `domainDocumentReader` derives the binder mapper exactly like
`resourceBinder`; `fromDocument(JsonApiDocument)` binds an already-validated document without
re-parsing. The envelope is a public low-level domain-binding result, not a required application
controller/service abstraction: a Spring adapter may unwrap its primary payload
into the application's declared DTO type, so applications can consume typed DTOs without depending
on `JsonApiDomainDocument` (the envelope stays available for document metadata, `included`, or
explicit representation-state access).

`patchCommandReader` forces `DocumentUsage.UPDATE_REQUEST` and `PrimaryDataKind.RESOURCE` for validate-on-read,
binds only supplied mapped attributes and relationships into a `PatchCommand` (never a complete
DTO), never reads `included`, and keeps binder failures as resource-relative `JsonApiMappingException`
pointers. Pass optional `EndpointIdentity` on the factory `ValidationContext`. Applications own
authorization and command application.

`patchDtoReader` uses the same validate-on-read contract and binds the update **directly** into an
 application-owned annotated PATCH DTO: every attribute and relationship member must be declared
 exactly as `PatchPresence<T>` (wrapper-level `@JsonDeserialize`/`@JsonSerialize` customization —
 `using`, `converter`, key/content/null customizers, typing, type refinement, and mix-ins — on such
 a member is rejected with `INVALID_PATCH_PROPERTY_TYPE`; inner-`T` customization stays supported),
 omitted members become `PatchPresence.omitted()`, supplied members become `PatchPresence.present(...)`
 (explicit JSON `null` / null linkage becomes `present(null)`, or `present(Optional.empty())` for an
 `Optional` inner type), and supplied members unknown to the PATCH DTO fail with
 `UNKNOWN_PATCH_MEMBER` (the low-level path silently ignores them — direct binding rejects).
 Parameterized `JavaType` targets (e.g. `GenericPatch<String>`) keep their type bindings through
 mapping, conversion, and construction. The caller mapper is never mutated; the binder mapper is
 derived via `rebuild()` plus an internal `PatchPresence` module whose internal marker always
 serializes with the exact `present`/`value` member names, so the tri-state is invariant to caller
 `JsonInclude` inclusion config **and** caller property naming strategies. See
 [ADR-013](../docs/adr/013-direct-typed-patch-dto-binding.md).

 Recursive structured attributes (see [ADR-014](../docs/adr/014-recursive-structured-value-patch-semantics.md))
 work on **both** paths through a shared location-agnostic engine and the neutral `StructuredPatch`
 payload:

 - **Typed path (opt-in):** a nested member whose inner type is a presence-aware PATCH shape (every
   visible member exactly `PatchPresence<T>`, no wrapper-level customization) recurses when its wire
   value is a JSON object; nested `omitted` / `present(value)` / `present(null)` are preserved
   recursively, `PatchPresence<Optional<X>>` unwraps/rewraps (`null` → `present(Optional.empty())`),
   an explicit empty object binds to a shape with every member omitted, and containers
   (`List`/`Set`/array/`Map`) stay atomic replacement values. Mixed, raw-`PatchPresence`,
   direct-`Present`, and wrapper-customized nested shapes are invalid declarations, validated lazily
   only when the nested member is actually bound.
 - **Low-level path (ordinary domain):** a supplied attribute whose declared type is an ordinary
   traversable structured domain bean (records, JavaBean-style getter/setter classes, or
   constructor-bound types — not containers/scalars/custom-deserialized types) and whose wire value
   is a JSON object binds to an `AttributeChange` whose `value` is a `StructuredPatch` of
   supplied-only nested changes instead of a fully materialized replacement bean (a deliberate,
    documented behavior change with migration guidance in ADR-014; there is no opt-out for this
    behavior).
   `Optional<X>` is a transparent qualification wrapper for the traversal decision: members that
   recurse produce an un-wrapped `StructuredPatch`, while atomic members keep the declared
   `Optional` wrapper in their `Atomic` payload (e.g. nested `null` binds to
   `Atomic(Optional.empty())`), a
   single `PatchPresence<T>` wrapper is unwrapped, and nested unknown members are skipped (lossless).
   A `PatchPresence<T>` member wrapping a presence-aware PATCH shape is rejected on this path with
   `INVALID_PATCH_PROPERTY_TYPE` — presence-aware PATCH shapes are a typed-path concept.

 Nested members bind by their Jackson-resolved **wire name** only. `wireName` is the document member
 name used for lookup and diagnostics; `logicalName` is carried in each `StructuredMember` solely for
 application-property correspondence and is never treated as an automatic JSON input alias (under a
 naming strategy, the Java logical name does not bind). Nested atomic conversion preserves the
 applicable configured Jackson deserialization semantics, including property-scoped customization
 (`@JsonDeserialize using = ...`, converters, content/key deserializers, type refinement) — both
 paths share one location-neutral property-scoped conversion collaborator, so a supplied nested
 member converts through the same authority Jackson would use for that property.

Diagnostic locations for nested failures are engine-accumulated wire-name locations (e.g.
  `/attributes/address/street`), escaped per RFC 6901 through `MappingLocation`; final typed-path
  construction failures are shape-translated to the wire-name location.

 Whole-object resource-side meta mapping (see [ADR-015](../docs/adr/015-flat-whole-object-meta-mapping.md))
  maps the complete `ResourceObject.meta` / `Relationship.meta` object to one application-owned
  property per location, on read, write, and both PATCH paths:

  - **Annotations:** `@JsonApiMeta` maps resource meta; `@JsonApiRelationshipMeta(relationship =
    "author")` maps the meta of the mapped relationship whose Jackson property identity is `author`
    (required). Mapping then uses that relationship's configured-Jackson external name on the wire,
    so renaming the relationship through Jackson carries its meta. At most one meta property per
    location.
  - **Targets:** Bean / `Map` / `Object`, with at most one `Optional` wrapper (read/write
    and low-level PATCH); typed PATCH DTOs declare exactly `PatchPresence<T>` with at most one
    `Optional` inside. Scalars, containers, and nested wrapper chains are rejected with a stable
    meta diagnostic at the consuming entry point.
  - **Write** converts through the mapped property's fully contextualized Jackson property writer
    (including property serializers and mix-ins) and requires a `Map` result before constructing
    core `Meta`; invalid member names / non-object runtime values fail with `INVALID_META_TARGET`.
    **Read** binds members under the mapped property's logical name.
  - **PATCH:** the typed path binds `PatchPresence` meta through the recursive structured-value
    PATCH engine defined by ADR-014 (presence-aware nested shapes, `{}` = present-with-all-omitted,
    atomic map targets) and rejects supplied
    meta without a matching member; the low-level path binds `ResourceMetaChange` /
    `RelationshipMetaChange` (structured beans recurse to `StructuredPatch`, maps stay atomic) and
    skips unmapped meta. Relationship meta participates only when the relationship carries `data`.
    `PatchCommand.changes()` order is deterministic: resource meta first, then attributes, then
    relationships with their meta immediately after the linkage change.

  Per-linkage identifier meta (see [ADR-017](../docs/adr/017-resource-identifier-meta-mapping.md))
  is an opt-in `RelationshipLinkage<T, M>` on the relationship property:

  ```java
  @JsonApiRelationship
  RelationshipLinkage<Person, AuthorMeta> author;

  @JsonApiRelationship
  List<RelationshipLinkage<Comment, CommentMeta>> comments;
  ```

  - **Opt-in:** ordinary `@JsonApiRelationship Person` / `List<Comment>` / `Set` / array /
    `Optional` / `ResourceIdentifier` / custom linkage mapping remain unchanged when identifier
    meta is not needed.
  - **Transparency:** `target` is mapped by the existing relationship pipeline. `meta` maps only to
    `ResourceIdentifier.meta`. `@JsonApiRelationshipMeta` continues to own `Relationship.meta`.
  - **To-many:** each wrapper element owns its identifier meta. `Set<RelationshipLinkage<…>>` is
    valid because association is structural.
  - **Null meta:** `meta == null` is no overlay; existing `ResourceIdentifier.meta` on a direct
    identifier target is preserved. Write overlay keeps `type` / `id` / `lid` /
    `additionalMembers`.
  - **PATCH:** identifier meta is not independently patchable. Typed
    `PatchPresence<RelationshipLinkage<T, M>>` and low-level `RelationshipChange` replace whole
    linkage, including any identifier meta. There is no identifier-meta `PatchChange` and no
    element-addressed mutation.

By default, `@JsonApiId` and `@JsonApiLocalId` values become the JSON:API `"id"` / `"lid"` strings
via `Object.toString()`. One `IdentifierConverter` serves both identity roles: their Java scalar
conversion is equivalent, only the wire member differs. Pass an
`IdentifierConverter` to `resourceMapper`, `resourceBinder`, `patchCommandReader`, or `patchDtoReader`
only
when you need a different wire form; read binding inverts it through `IdentifierConverter.parse(String)`.

Mapped ordinary values use configured Jackson at the property boundary: resource attributes and
mapped resource/relationship meta write through their contextualized property serializers, while
`RelationshipLinkage` identifier meta converts through configured Jackson against the wrapper's
meta `JavaType`. Flat reads and supplied PATCH values use the contextualized property deserializer after any
JSON:API-specific conversion. JSON:API remains authoritative for the identifier wire string,
relationship linkage, and `PatchPresence` state; those adapter-owned states are not replaced by a
property serializer or deserializer. If no mapped property can be resolved, the adapter retains its
ordinary type/module conversion fallback.

`JsonApiJackson3.writer` / `resourceMapper` / `resourceBinder` / `patchCommandReader` /
`patchDtoReader`
derive isolated mappers via `rebuild()`; `reader` uses the supplied mapper directly for token-driven
decoding, and `domainDocumentReader` uses it for decoding while deriving its binder mapper. No
construction path mutates the caller's mapper. Writers validate before emission. Readers decode
through public core constructors, then run aggregate validation. Mappers and binders introspect
types for resource metadata. Derived mapping and PATCH binder mappers register the internal
`MetaBindingModule` so built-in `ResourceIdentifier` values can round-trip identifier meta; the
PATCH DTO binder additionally registers the internal `PatchPresence` module. None of those paths
register the JSON:API document module.

## Non-goals

HTTP `fields[TYPE]` parsing and field authorization beyond the explicit `FieldPolicy` allow-list
remain application/adapter responsibilities. Domain graph hydration and
persistence lookup remain out of scope. Command application (mutating domain or persistence
objects from a `PatchCommand`) remains application-owned. Jackson 2 parity is a separate
artifact; both majors share the neutral contracts of
[jsonapi-java-jackson-api](../jsonapi-java-jackson-api/README.md) per [ADR-007](../docs/adr/007-module-boundaries.md).

## Further reading

- [Architecture overview](../docs/architecture.md)
- [Conformance checklist](../docs/conformance.md)
- [ADR-002 — Wire states](../docs/adr/002-document-representation.md)
- [ADR-004 — Jackson integration](../docs/adr/004-jackson-integration.md)
- [ADR-005 — Domain mapping and inclusion](../docs/adr/005-domain-mapping-and-inclusion.md)
- [ADR-006 — Document-first reads](../docs/adr/006-read-boundary.md)
- [ADR-007 — Module boundaries](../docs/adr/007-module-boundaries.md)
- [ADR-009 — JSpecify nullness](../docs/adr/009-jspecify-nullness.md)
- [ADR-010 — Architectural tests](../docs/adr/010-architectural-tests.md)
- [ADR-011 — Flat DTO reads](../docs/adr/011-flat-dto-read-binding.md)
- [ADR-012 — Resource PATCH binding](../docs/adr/012-resource-patch-binding.md)
- [ADR-013 — Direct typed PATCH DTO binding](../docs/adr/013-direct-typed-patch-dto-binding.md)
- [ADR-014 — Recursive structured value PATCH semantics](../docs/adr/014-recursive-structured-value-patch-semantics.md)
- [ADR-015 — Flat whole-object mapping for resource-side meta](../docs/adr/015-flat-whole-object-meta-mapping.md)
- [ADR-016 — Mapper-instance construction for Jackson adapters](../docs/adr/016-jackson-adapter-construction.md)
- [ADR-017 — Opt-in RelationshipLinkage for resource identifier meta](../docs/adr/017-resource-identifier-meta-mapping.md)
- [Canonical fixtures](../jsonapi-java-jackson-api/src/testFixtures/resources/jsonapi/corpus/1.1/README.md)
- [Jackson API module](../jsonapi-java-jackson-api/README.md)
- [Root agent workflow](../AGENTS.md)

## For contributors / agents

- **Level-1 runtime composes capabilities:** `Jackson3JsonApi` (built via `JsonApiJackson3.builder`
  or `JsonApiJackson3.jsonApi`) owns no mapping, codec, or validation logic of its own; each facet
  delegates to the capability above with fixed usages (response defaults, forced create/update, and
  identifier-kind linkage reads). Strict primary-shape mismatches fail as `JsonApiMappingException`
  with `RESOURCE_TYPE_MISMATCH` at `/data`. Never add facade-level naming, relationship-presence,
  or exception types.
- **Validate then write / read then validate:** `JsonApiDocumentWriter` and `JsonApiDocumentReader`
  are the sole public codec paths. Failures preserve stable diagnostics (`ValidationRuleCode` +
  JSON Pointer-like path; reads also carry `CodecFailureCategory` and safe source location). Do not
  expose the codec mapper publicly.
- **Map then write:** `JsonApiResourceMapper` produces core model objects; feed them to a writer
  for serialization. Mapping uses Jackson's logical property model and caches `ResourceMapping`
  by complete declared type and mapper config identity. Ordinary concrete roots infer that type;
  direct parameterized roots use the `JavaType` overloads. Configured Jackson is the single authority for class-level
  resource metadata: `@JsonApiResource` is read through mapper introspection, so class-level
  mix-ins provide or override it exactly as for ordinary Jackson serialization — across domain
  write, flat binding, both PATCH paths, registry key derivation, and declared to-many element
  types. Mapping diagnostics use `MappingDiagnostic` + domain class rather than core validation
  codes.
- **Mapping-location contract:** every `JsonApiMappingException` carries an optional
  location that is either absent (`null`) or a valid RFC 6901 JSON Pointer built through
  `MappingLocation`, whose segments are individually escaped (`~` to `~0`, `/` to `~1`). Producers
  mapping one resource object emit resource-relative pointers over JSON:API member names:
  `/type`, `/id`, `/lid`, `/attributes/<wire-name>`, `/relationships/<wire-name>/data`,
  `/meta`, `/relationships/<wire-name>/meta`, `/relationships/<wire-name>/data/meta`,
  `/relationships/<wire-name>/data/<index>/meta`. Jackson logical property names are translated
  through the mapping before they appear in a location — a logical name is never reinterpreted as
  pointer syntax. Failures without a meaningful member coordinate (missing annotations, invalid
  type names, registry conflicts, include-path and fieldset specification errors) carry an absent
  location; the identifying names stay in the message. Absence is never encoded as `""` or `/`.
- **Validate then bind:** `JsonApiResourceBinder` binds already-validated `ResourceObject` values
  to flat DTOs; it never parses JSON, never reads document `included`, and assembles no domain
  graph. `fromResource`/`fromResources` validate `type` against the target's configured
  `@JsonApiResource` metadata (mix-ins honored) and report `MappingDiagnostic` plus a
  resource-relative pointer per the mapping-location contract above. Bean-construction failures
  translate their Jackson failure path into the mapped member's wire pointer (deeply, through
  resolved shape metadata); unmappable paths carry no location. Missing members are omitted;
  explicit JSON `null` binds null; relationship linkage binds `ResourceIdentifier` (plus
          Optional/List/Set/array shapes) directly, and any other target class needs a registered
   `RelationshipLinkageMapper`. To-many `RelationshipLinkage` collections map each identifier
   occurrence as `T` before wrapping, so a custom mapper cannot reorder target/meta ownership.
   Read bindability follows Jackson's deserialization metadata rather
   than the serialization accessor: supplied getter-only or otherwise non-deserializable mapped
   members fail with `NON_DESERIALIZABLE_PROPERTY`. Bind failures throw
   `JsonApiMappingException`, never `JsonApiDocumentReadException`.
- **Typed domain envelope:** `JsonApiDomainDocumentReader` composes the document reader with the
  flat DTO binder. Primary and included resources bind only through the `ResourceTypeRegistry`
  (keyed by each registered raw class's configured class-level resource metadata; build it with
  `ResourceTypeRegistry.builder(callerMapper)` so keys and binding agree on configured metadata).
  Reader construction re-resolves every registered key against its own configured metadata and
  rejects disagreement with `RESOURCE_TYPE_MISMATCH` and no location; distinct mapper instances
  with agreeing keys remain usable;
  unregistered types fail with `UNREGISTERED_RESOURCE_TYPE` at the document pointer
  (`/data`, `/data/n`, `/included/n`), duplicate type registrations fail at `build()` with
  `CONFLICTING_TYPE_REGISTRATION` and no location. Identifier primary data never binds; absent
  `included` stays null while `included: []` is a non-null empty `IncludedResources` with dual
  id/lid identity lookup. Binder failures compose structurally with the document prefix via
  `MappingLocation.append`: `/data/2` + `/attributes/title` = `/data/2/attributes/title`; a binder
  failure without a location reports just the prefix. Envelope collections are defensively copied
  at construction and unmodifiable; `metaAs` reuses the reader-derived binder mapper (never a
  fresh default mapper) and reports the document-relative `/meta` on conversion failure. No
  relationship injection: `included` DTOs are independently listed/indexed only.
- **Identifier round-trip:** read binding calls `IdentifierConverter.parse(String)` on the wire
  identifier (of either identity role: `/id` for the id role, `/lid` for the local-id role), then
  applies the target property's configured Jackson deserializer to the parsed
  intermediate (normal flat reads and typed PATCH construction use the synthetic property map;
  low-level PATCH uses the shared property-scoped converter). Custom write converters must override
  `parse` to invert their wire form. Write mapping emits the id role to `ResourceObject.id` and the
  local-id role to `ResourceObject.lid` — never promoting a local identifier to `id`; when both
  identity values are absent the write fails with `MISSING_IDENTIFIER` at the id property's
  location, or at `/lid` for a lid-only type. A null local-id value means `lid` is absent, so an
  id-only domain value never needs a local id.
- **PATCH identity:** identity comes from resource `id` only on both PATCH paths (no `lid`
  projection); `@JsonApiLocalId` properties never become patchable members.
- **Opt-in inclusion:** Compound `included` resources require `RepresentationSelection` and
  `RepresentationPolicy` on mapper overloads (`resource`/`collection`, nullable `DocumentEnvelope`,
  selection, policy). `IncludePolicy` gates inclusion traversal only; linkage on selected resources remains
  full when fieldsets are empty. Empty include paths omit `included`; a non-empty request that
  resolves to nothing emits `included: []`. Defaults are deny-all with finite depth/count limits.
  Inclusion deduplication is alias-aware: a resource carrying both `id` and `lid` registers both
  identity keys (matching core validation's id↔lid partner binding), so a primary is recognized
  under any of its aliases and included occurrences of one resource deduplicate regardless of which
  alias the reaching occurrence carries; unequal representations sharing an identity alias fail
  with `CONFLICTING_INCLUDED_REPRESENTATION` at mapping time.
- **Sparse fieldsets:** `RepresentationSelection.fieldsets()` + `FieldPolicy` select attributes and
  relationships by final JSON:API names (absent type key = unrestricted; present empty list selects
  no attributes/relationships, while non-field resource members such as mapped resource meta remain
  independent). Applied only by `toMappedDocument` / `toMappedCollectionDocument`; non-mapped
  `toDocument` / `toCollectionDocument` overloads reject a non-empty fieldset map with
  `FIELDSETS_REQUIRE_MAPPED_DOCUMENT`. Inclusion traversal may still follow fieldset-excluded
  relationships on validated include paths; the resulting `MappedDocument` carries sparse-fieldset
  linkage-exemption provenance — the identities of included resources whose linking relationship an
  applied fieldset removed. Writing a `MappedDocument` through any `JsonApiDocumentWriter` output
  form composes that provenance into the writer's bound validation context before validation, so
  callers never translate mapping provenance into validation policy themselves. Exemptions are
  scoped: exempted included resources count as reachable (their own relationships still extend
  reachability to their subtrees), while every other included resource still requires full linkage,
  so unrelated linkage defects keep failing validation.
- **Primary-data kind:** Ambiguous `{"type","id"}` and `[]` require explicit `PrimaryDataKind` on
  `DocumentReadContext`; never guess from object members.
- **Wire states:** Omit members for Java `null` components; emit/decode JSON `null` for sealed
  null variants; emit/decode `{}` / `[]` for present-empty wrappers and empty collections.
  Serialize flat wrappers from `flatten()` / `Meta.members()`.
- **Mapper isolation:** Factories accept configured `JsonMapper` instances, never builders, and do
  not mutate them. Token-driven document reading uses the supplied mapper directly; other
  capabilities derive isolated mappers only where their implementation requires it. Close only
  parsers created by convenience overloads; leave caller-owned streams/parsers open.
- **Nullness:** Production packages are `@NullMarked` (JSpecify only). Use `@Nullable` for
  absence and intentionally null map values. Do not import `core.internal`.
- **Mapping grammar:** JSON:API member-name validation delegates to
  `core.validation.MemberNames`. Do not import `core.internal`.
- **Presence-aware PATCH:** This name is the omitted / explicit-null / present update contract
  (ADR-012), not the typed nested-shape declaration in ADR-014. `JsonApiPatchCommandReader` validates
  with
  forced `UPDATE_REQUEST` usage, then binds only supplied mapped attributes and relationships into
  a common `PatchCommand`. Never call `JsonApiResourceBinder` / whole-DTO construction, never read
  `included`, never prefix binder pointers with `/data`. Explicit attribute JSON `null` stores
  `value == null` (including Optional properties). Identity comes from resource `id` only (no
  `lid` fallback) and is never a change. Adjacent terms are listed in
  [architecture](../docs/architecture.md#terminology).
- **Direct typed PATCH DTO:** `JsonApiPatchDtoReader` shares the same validate-on-read contract and
  binds the update into an annotated PATCH DTO whose patchable members are exactly
  `PatchPresence<T>`. Both paths share `PatchMemberConverter` (per-member conversion against an
  explicit target `JavaType`); the DTO path converts through the unwrapped inner type and wraps in
  `Present`/`Omitted`. Declaration violations (`INVALID_PATCH_PROPERTY_TYPE`) and unknown supplied
  members (`UNKNOWN_PATCH_MEMBER`) fail at bind time; the declaration check covers role-annotated
  members and rejects every wrapper-level Jackson customization path (custom `using`
  serializers/deserializers, converters, key/content/null customizers, typing, type refinement,
  mix-ins). Construction uses the synthetic-map + `convertValue` strategy (ADR-004) with an
  internal `PresenceMarker` + `PatchPresenceModule`; the marker's serializer always emits the exact
  `present`/`value` member names (invariant to caller naming strategies and inclusion config) and
  the deserializer fails loudly on any other marker shape. Never register a `PatchPresence`
  deserializer on the caller's mapper.
- **Architectural tests:** `Jackson3DependencyRulesSpec` allows JDK, JSpecify, core public
  packages, annotations, the common contracts package, `tools.jackson..`, and this module; bans
  `core.internal` and Jackson 2 (`com.fasterxml.jackson..`) in production sources, and asserts no
  moved common-contract type is re-declared here (ADR-010).
- **Tests:** Spock specs under `src/test/groovy/` are boring and explicit: setup, invoke the production API, assert directly. Shared test fixtures contain passive DTOs, canonical JSON/schema resources, and the neutral `TestFixtureResources` loader via `jsonapi-java-jackson-api` test fixtures. Behavioral assertions belong in each adapter's own tests. Do not introduce shared test orchestration, scenario registries, or assertion frameworks. Small duplication between adapter test suites is acceptable. Keep Jackson-major-specific fixture shapes in small `*Fixtures.java` containers next to the owning spec.
