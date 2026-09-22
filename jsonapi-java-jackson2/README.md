# jsonapi-java-jackson2

Native Jackson 2 implementation of the major-neutral Level-1 JSON:API contract, plus advanced
document codec, mapping, flat binding, typed-envelope, and PATCH capabilities.

## Packages

| Package | Responsibility |
|---------|----------------|
| [`com.kazforge.jsonapi.jackson2`](src/main/java/com/kazforge/jsonapi/jackson2/package-info.java) | Public configured runtime, factory, codec, mapping/binding, typed-envelope, and PATCH entry points |
| [`com.kazforge.jsonapi.jackson2.mapping`](src/main/java/com/kazforge/jsonapi/jackson2/mapping/package-info.java) | Public [`RelationshipLinkageMapper`](src/main/java/com/kazforge/jsonapi/jackson2/mapping/RelationshipLinkageMapper.java) contract |
| [`com.kazforge.jsonapi.jackson2.internal`](src/main/java/com/kazforge/jsonapi/jackson2/internal/package-info.java) | Mapping, binding, PATCH, and module implementation; unsupported API |
| [`com.kazforge.jsonapi.jackson2.internal.codec`](src/main/java/com/kazforge/jsonapi/jackson2/internal/codec/package-info.java) | Self-contained token codec implementation; unsupported API |

## Start with Level 1

```java
import com.fasterxml.jackson.databind.json.JsonMapper;

JsonMapper mapper = JsonMapper.builder().build();
Jackson2JsonApi api = JsonApiJackson2.jsonApi(mapper);

String json = api.resources().writeOne(article);
Article readBack = api.resources().readOne(json, Article.class);
ArticlePatch patch = api.patches().readPatch(updateJson, ArticlePatch.class);
```

The runtime implements the neutral [`JsonApi`](../jsonapi-java-api/README.md) facets:
resources, linkage relationships, raw documents, and PATCH. Resource reads are strict and
homogeneous; create/update authoring selects the corresponding core validation usage. Use
`JsonApiJackson2.builder(mapper)` for application-lifetime identifier conversion, linkage mappers,
representation policy, decorators, or an optional default `jsonapi.version`. Selection, document
envelopes, and expected update identity remain per-operation values.

## Advanced entry points

[`JsonApiJackson2`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiJackson2.java) creates each
capability from a caller-configured `JsonMapper`:

| Capability | Public API |
|------------|------------|
| Validated document codec | [`JsonApiDocumentReader`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDocumentReader.java), [`JsonApiDocumentWriter`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDocumentWriter.java) |
| Domain mapping and flat binding | [`JsonApiResourceMapper`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiResourceMapper.java), [`JsonApiResourceBinder`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiResourceBinder.java) |
| Heterogeneous typed documents | [`JsonApiDomainDocumentReader`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocumentReader.java), [`JsonApiDomainDocument`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiDomainDocument.java) |
| Presence-aware PATCH | [`JsonApiPatchCommandReader`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchCommandReader.java), [`JsonApiPatchDtoReader`](src/main/java/com/kazforge/jsonapi/jackson2/JsonApiPatchDtoReader.java) |

Advanced mapping accepts a complete Jackson `JavaType` when a parameterized root cannot be recovered
from its runtime class. Typed documents use an explicit neutral `ResourceTypeRegistry`; included DTOs
remain independently bound rather than being injected into relationships. Shared representation,
meta, identifier, decoration, and PATCH semantics are owned by
[`jsonapi-java-api`](../jsonapi-java-api/README.md); backend-neutral compound-inclusion traversal and
inclusion state, and the basic and advanced resource write semantics for identity, attributes,
ordinary and advanced relationship linkage, fieldsets, relationship-member assembly, and
resource/relationship/identifier meta application, are owned by
[`jsonapi-java-mapping`](../jsonapi-java-mapping/README.md). This adapter supplies the native
capability bridges over configured Jackson, including declared relationship-shape and target
resolution and property-scoped whole-meta and declared-type identifier-meta conversion. Public
Javadocs and the linked ADRs own the remaining details rather than repeated here.

## Jackson 2 boundary

- Production integration uses `com.fasterxml.jackson.*` and must not import `tools.jackson.*` or
  `com.kazforge.jsonapi.core.internal`.
- Advanced reader and writer APIs retain checked `IOException`. Level-1 operations adapt unavoidable
  stream failures to `UncheckedIOException`; payload failures remain in the document-read family.
- Caller-owned streams, writers, parsers, and generators remain open. Convenience-created parser or
  generator resources are closed by the adapter.
- Factories never mutate the caller mapper. Derived mapping/binding mappers install JDK 8
  `Optional` support only when a behavioral probe shows the caller configuration does not already
  provide it.
- The public composition package may depend on mapping and the two internal responsibilities;
  mapping and `internal.codec` do not depend back on composition or on sibling internals.
- Supported signatures do not expose shared `com.kazforge.jsonapi.internal` helpers, and
  this module does not redeclare neutral contract types.
- Jackson 2 property-writer, serializer/deserializer, and checked-I/O mechanics stay adapter-local.

## Non-goals

This module does not provide HTTP or media-type policy, query parsing, field authorization,
persistence lookup or projection execution, graph hydration, PATCH authorization, or mutation of
application state. It does not detect another Jackson major at runtime.

See the [architecture overview](../docs/architecture.md),
[conformance checklist](../docs/conformance.md),
[ADR-015](../docs/adr/015-jackson-adapter-construction.md), and
[ADR-018](../docs/adr/018-level-one-application-api-contract.md).
