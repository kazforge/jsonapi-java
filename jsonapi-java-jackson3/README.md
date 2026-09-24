# jsonapi-java-jackson3

Native Jackson 3 implementation of the major-neutral Level-1 JSON:API contract, plus advanced
document codec, mapping, flat binding, typed-envelope, and PATCH capabilities.

## Packages

| Package | Responsibility |
|---------|----------------|
| [`com.kazforge.jsonapi.jackson3`](src/main/java/com/kazforge/jsonapi/jackson3/package-info.java) | Public configured runtime, factory, codec, mapping/binding, typed-envelope, and PATCH entry points |
| [`com.kazforge.jsonapi.jackson3.mapping`](src/main/java/com/kazforge/jsonapi/jackson3/mapping/package-info.java) | Public [`RelationshipLinkageMapper`](src/main/java/com/kazforge/jsonapi/jackson3/mapping/RelationshipLinkageMapper.java) contract |
| [`com.kazforge.jsonapi.jackson3.internal`](src/main/java/com/kazforge/jsonapi/jackson3/internal/package-info.java) | Mapping, binding, PATCH, and module implementation; unsupported API |
| [`com.kazforge.jsonapi.jackson3.internal.codec`](src/main/java/com/kazforge/jsonapi/jackson3/internal/codec/package-info.java) | Self-contained token codec and wire-helper implementation; unsupported API |

## Start with Level 1

```java
import tools.jackson.databind.json.JsonMapper;

JsonMapper mapper = JsonMapper.builder().build();
Jackson3JsonApi api = JsonApiJackson3.jsonApi(mapper);

String json = api.resources().writeOne(article);
Article readBack = api.resources().readOne(json, Article.class);
ArticlePatch patch = api.patches().readPatch(updateJson, ArticlePatch.class);
```

The runtime implements the neutral [`JsonApi`](../jsonapi-java-api/README.md) facets:
resources, linkage relationships, raw documents, and PATCH. Resource reads are strict and
homogeneous; create/update authoring selects the corresponding core validation usage. Use
`JsonApiJackson3.builder(mapper)` for application-lifetime identifier conversion, linkage mappers,
representation policy, decorators, or an optional default `jsonapi.version`. Selection, document
envelopes, and expected update identity remain per-operation values.

## Advanced entry points

[`JsonApiJackson3`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiJackson3.java) creates each
capability from a caller-configured `JsonMapper`:

| Capability | Public API |
|------------|------------|
| Validated document codec | [`JsonApiDocumentReader`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiDocumentReader.java), [`JsonApiDocumentWriter`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiDocumentWriter.java) |
| Domain mapping and flat binding | [`JsonApiResourceMapper`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiResourceMapper.java), [`JsonApiResourceBinder`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiResourceBinder.java) |
| Heterogeneous typed documents | [`JsonApiDomainDocumentReader`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiDomainDocumentReader.java), [`JsonApiDomainDocument`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiDomainDocument.java) |
| Presence-aware PATCH | [`JsonApiPatchCommandReader`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiPatchCommandReader.java), [`JsonApiPatchDtoReader`](src/main/java/com/kazforge/jsonapi/jackson3/JsonApiPatchDtoReader.java) |

Advanced mapping accepts a complete Jackson `JavaType` when a parameterized root cannot be recovered
from its runtime class. Typed documents use an explicit neutral `ResourceTypeRegistry`; included DTOs
remain independently bound rather than being injected into relationships. Shared representation,
meta, identifier, decoration contracts, and PATCH semantics are owned by
[`jsonapi-java-api`](../jsonapi-java-api/README.md); backend-neutral compound-inclusion traversal and
inclusion state, the basic and advanced resource write semantics for identity, attributes,
ordinary and advanced relationship linkage, fieldsets, relationship-member assembly,
resource/relationship/identifier meta application, and additive resource/relationship link
decoration, and the basic and advanced resource read semantics for resource-type matching,
identity-role selection, attribute/relationship presence and order, synthetic-input assembly
preserving absent-versus-explicit-null, relationship cardinality, null/empty short-circuiting,
direct-identifier copying, `RelationshipLinkage` occurrence pairing, resource/relationship meta
binding, top-level construction-start backend-name to JSON:API location translation, and
backend-neutral low-level `PatchCommand` orchestration (required `id` identity, supplied-member
classification, effective-deserialization bindability enforcement, change construction, and the
contract phase order, sharing relationship-linkage orchestration with reads), and the
backend-neutral recursive structured-value engine and typed `PatchPresence<T>` DTO contract phase
order, are owned by [`jsonapi-java-mapping`](../jsonapi-java-mapping/README.md). This adapter
supplies the native
capability bridges over configured Jackson, including declared write relationship-shape and target
resolution, lazy read relationship-shape resolution with configured linkage-mapper selection and
invocation, property-scoped whole-meta and declared-type identifier-meta conversion, the
effective-type resolution that decoration looks up, configured wire-identifier parsing,
effective deserialization property discovery, nested construction-path walking, native
failure-path extraction, declared meta-target validation against effective inbound PATCH property
types, identity and attribute/meta conversion, configured structured-shape introspection and
caching, native atomic conversion, typed relationship conversion and identity parsing, single bean
construction, final relationship
container coercion, and the configured decoration registry. Public
Javadocs and the linked ADRs own the remaining details rather than repeated here.

## Jackson 3 boundary

- Production integration uses `tools.jackson.*` and must not import `com.fasterxml.jackson.*` or
  `com.kazforge.jsonapi.core.internal`.
- Public advanced APIs follow Jackson 3's unchecked exception model; caller-owned streams, writers,
  parsers, and generators remain open.
- Factories never mutate the caller mapper. Capabilities derive isolated mappers only when native
  modules or introspection state require it.
- The public composition package may depend on mapping and the two internal responsibilities;
  mapping and `internal.codec` do not depend back on composition or on sibling internals.
- Supported signatures do not expose the unsupported `com.kazforge.jsonapi.mapping.internal`
  helpers or this adapter's `internal` implementation packages, and this module does not redeclare
  neutral contract types.
- Jackson 3 property, serializer/deserializer, and PATCH-marker mechanics stay adapter-local. The
  `PatchPresence` module is registered only on derived typed-PATCH mappers, never the caller mapper.

## Non-goals

This module does not provide HTTP or media-type policy, query parsing, field authorization,
persistence lookup or projection execution, graph hydration, PATCH authorization, or mutation of
application state. It does not detect another Jackson major at runtime.

See the [architecture overview](../docs/architecture.md),
[conformance checklist](../docs/conformance.md),
[ADR-015](../docs/adr/015-jackson-adapter-construction.md), and
[ADR-018](../docs/adr/018-level-one-application-api-contract.md).
