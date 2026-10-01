# jsonapi-java-api

Backend-independent application contracts and values implemented by the configured Jackson 2 and
Jackson 3 runtimes. This module defines contracts and values; it has no standalone Jackson
runtime.

## Packages and entry points

| Package | Responsibility |
|---------|----------------|
| [`com.kazforge.jsonapi`](src/main/java/com/kazforge/jsonapi/package-info.java) | Supported-package overview and cross-package invariants |
| [`com.kazforge.jsonapi.api`](src/main/java/com/kazforge/jsonapi/api/package-info.java) | Level-1 [`JsonApi`](src/main/java/com/kazforge/jsonapi/api/JsonApi.java) root and resource, relationship, document, and PATCH facets |
| [`com.kazforge.jsonapi.document`](src/main/java/com/kazforge/jsonapi/document/package-info.java) | Document read contexts, primary-data kind, and write envelope |
| [`com.kazforge.jsonapi.mapping`](src/main/java/com/kazforge/jsonapi/mapping/package-info.java) | Mapping, provenance, decoration, typed-envelope, registry, and identifier-meta contracts |
| [`com.kazforge.jsonapi.patch`](src/main/java/com/kazforge/jsonapi/patch/package-info.java) | Presence, command, change, and structured PATCH contracts |
| [`com.kazforge.jsonapi.representation`](src/main/java/com/kazforge/jsonapi/representation/package-info.java) | Include/fieldset selection and application policy |
| [`com.kazforge.jsonapi.diagnostic`](src/main/java/com/kazforge/jsonapi/diagnostic/package-info.java) | Stable codec/mapping diagnostics and locations |

Each package's documentation owns its invariants. The packages carry backend-independent contract
and value shapes only: configured Jackson in each adapter derives the observable property
semantics, and backend-neutral mapping implementation lives in the unsupported
[`jsonapi-java-mapping`](../jsonapi-java-mapping/README.md) module.

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
typed envelopes remain adapter capabilities. [ADR-012](../docs/adr/012-level-one-application-api-contract.md)
owns that split.

## Boundary

- No production signature imports `tools.jackson.*`, `com.fasterxml.jackson.*`, or a major-specific
  adapter package.
- This artifact ships only supported contract packages. Cross-artifact mapping helpers live in
  `jsonapi-java-mapping`'s unsupported internal namespace, and native wire/codec helpers stay in
  each adapter's internal package; neither may appear in supported public signatures per
  [ADR-015](../docs/adr/015-responsibility-based-mapping-and-native-wire-codecs.md).

## Shared test fixtures

The `java-test-fixtures` variant provides passive, major-neutral application-shaped DTOs, the
[canonical JSON corpus](src/testFixtures/resources/jsonapi/corpus/1.1/README.md),
[pinned draft schemas](src/testFixtures/resources/jsonapi/schema/vendor/1.1-pr1603/README.md), the
neutral `TestFixtureResources` loader, and the shared characterization contract specs in
[`com.kazforge.jsonapi.fixtures.contract`](src/testFixtures/java/com/kazforge/jsonapi/fixtures/contract/package-info.java),
which every adapter runs through a concrete subclass. [`AGENTS.md`](../AGENTS.md) owns the fixture
and contract-spec policy.

See the [architecture overview](../docs/architecture.md) and
[conformance checklist](../docs/conformance.md).
