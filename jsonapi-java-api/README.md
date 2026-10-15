# jsonapi-java-api

Backend-independent application contracts and values implemented by the configured Jackson 3
runtime. This module defines contracts and values; it has no standalone Jackson
runtime. User workflows belong to the [public guide](https://jsonapi.kazforge.com/getting-started/).

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
semantics, and mapping implementation lives in the unsupported internal mapping package of
[`jsonapi-java-jackson3`](../jsonapi-java-jackson3/README.md).

## Level-1 contract

`JsonApi` groups ordinary resource, linkage-document, raw-document, and PATCH operations. Advanced
major-specific readers, writers, mapping/binding, parameterized Jackson types, and heterogeneous
typed envelopes remain adapter capabilities. [ADR-012](../docs/adr/012-level-one-application-api-contract.md)
owns that split.

## Boundary

- No production signature imports `tools.jackson.*`, `com.fasterxml.jackson.*`, or a major-specific
  adapter package.
- This artifact ships only supported contract packages. Mapping and native wire/codec helpers live
  in Jackson 3's distinct internal packages; neither may appear in supported public signatures per
  [ADR-016](../docs/adr/016-jackson-3-only-product-scope.md).

## Shared test fixtures

The `java-test-fixtures` variant provides passive application-shaped DTOs reused by Jackson 3 tests, the
[canonical JSON corpus](src/testFixtures/resources/jsonapi/corpus/1.1/README.md),
[pinned draft schemas](src/testFixtures/resources/jsonapi/schema/vendor/1.1-pr1603/README.md), and the
neutral `TestFixtureResources` loader. Behavioral characterization lives directly in the
[Jackson 3 contract specs](../jsonapi-java-jackson3/src/test/groovy/com/kazforge/jsonapi/jackson3/contract).
The
[fixture package documentation](src/testFixtures/java/com/kazforge/jsonapi/fixtures/package-info.java)
and [ADR-009](../docs/adr/009-architectural-tests.md) describe the passive-fixture boundary and
test ownership.

See the [architecture overview](../docs/architecture.md) and
[conformance checklist](../docs/conformance.md).
