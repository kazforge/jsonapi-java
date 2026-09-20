# jsonapi-java-jackson-api

Jackson-major-neutral contracts shared by the Jackson 2 and Jackson 3 adapters and by framework
integrations. This module defines contracts and values; it has no standalone Jackson runtime.

## Packages and entry points

| Package | Responsibility |
|---------|----------------|
| [`com.kazforge.jsonapi.jackson`](src/main/java/com/kazforge/jsonapi/jackson/package-info.java) | Supported-package overview and cross-package invariants |
| [`com.kazforge.jsonapi.jackson.api`](src/main/java/com/kazforge/jsonapi/jackson/api/package-info.java) | Level-1 [`JsonApi`](src/main/java/com/kazforge/jsonapi/jackson/api/JsonApi.java) root and resource, relationship, document, and PATCH facets |
| [`com.kazforge.jsonapi.jackson.document`](src/main/java/com/kazforge/jsonapi/jackson/document/package-info.java) | Document read contexts, primary-data kind, and write envelope |
| [`com.kazforge.jsonapi.jackson.mapping`](src/main/java/com/kazforge/jsonapi/jackson/mapping/package-info.java) | Mapping, provenance, decoration, typed-envelope, registry, and identifier-meta contracts |
| [`com.kazforge.jsonapi.jackson.patch`](src/main/java/com/kazforge/jsonapi/jackson/patch/package-info.java) | Presence, command, change, and structured PATCH contracts |
| [`com.kazforge.jsonapi.jackson.representation`](src/main/java/com/kazforge/jsonapi/jackson/representation/package-info.java) | Include/fieldset selection and application policy |
| [`com.kazforge.jsonapi.jackson.diagnostic`](src/main/java/com/kazforge/jsonapi/jackson/diagnostic/package-info.java) | Stable codec/mapping diagnostics and locations |
| `com.kazforge.jsonapi.jackson.internal.{wire,mapping,patch,representation}` | Unsupported Jackson-free helpers used only for adapter cooperation |

## Level-1 contract

An adapter supplies the implementation; application code can depend on the neutral interface:

```java
JsonApi api = /* Jackson 2 or Jackson 3 configured runtime */;

Article article = api.resources().readOne(json, Article.class);
String rendered = api.resources().writeOne(article);
ArticlePatch patch = api.patches().readPatch(updateJson, ArticlePatch.class);
```

`JsonApi` groups ordinary resource, linkage-document, raw-document, and PATCH operations. Advanced
major-specific readers, writers, mapping/binding, parameterized Jackson types, and heterogeneous
typed envelopes remain adapter capabilities. [ADR-018](../docs/adr/018-level-one-application-api-contract.md)
owns that split.

## Neutral contract boundaries

- No production signature imports `tools.jackson.*`, `com.fasterxml.jackson.*`, or a major-specific
  adapter package.
- `DocumentReadContext` keeps primary-data decoding kind separate from endpoint role.
- `RepresentationSelection` is operation-scoped; `RepresentationPolicy` is application/runtime
  configuration and is not complete authorization.
- `MappedDocument` carries sparse-fieldset provenance for the writer; callers do not translate it
  into validation policy.
- `PatchPresence.Present(null)` means explicit null, not omission. `PatchCommand` contains supplied
  changes only, and an empty `StructuredPatch` is a supplied empty object rather than clear-all.
- `RelationshipLinkage<T, M>` is the opt-in carrier for per-identifier meta.
- Core validation, document-read, and mapping diagnostics remain separate families. Mapping
  locations are absent or valid escaped JSON Pointers.
- The `internal` namespace is unsupported and must not appear in supported public signatures; native
  Jackson mechanics stay in each adapter per
  [ADR-019](../docs/adr/019-jackson-neutral-implementation-helpers.md).

## Shared test fixtures

The `java-test-fixtures` variant owns passive, major-neutral application-shaped DTOs, the
[canonical JSON corpus](src/testFixtures/resources/jsonapi/corpus/1.1/README.md),
[pinned draft schemas](src/testFixtures/resources/jsonapi/schema/vendor/1.1-pr1603/README.md), and the
neutral `TestFixtureResources` loader. Behavioral expectations and assertions remain in each
adapter's tests; this module provides no scenario registry or shared test orchestration.

The `com.kazforge.jsonapi.fixtures.contract` fixtures own the shared characterization contract
specs: abstract Spock specs asserting neutral Level-1 observable semantics that every adapter runs
through a concrete subclass supplying its configured runtime only. Contract specs stay at the
JSON:API member level and never freeze JSON object member ordering; fixture carriers there may use
Jackson-major-neutral annotations such as `@JsonProperty` purely as test mechanics, which are not
part of the backend-neutral contract and must not constrain future non-Jackson backends. New or
shared observable semantics for an extraction slice are first secured in a contract spec for that
slice, before ownership moves.

See the [architecture overview](../docs/architecture.md) and
[conformance checklist](../docs/conformance.md).
