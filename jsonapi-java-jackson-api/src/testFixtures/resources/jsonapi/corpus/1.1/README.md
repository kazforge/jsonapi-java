# JSON:API 1.1 document fixtures

Version-neutral JSON:API documents used by adapter tests. Jackson 2 and Jackson 3 tests share this
corpus as the normative shared wire-input inventory for semantics that must match across Jackson
majors; do not fork major-specific copies. The files ship as classpath resources under
`jsonapi/corpus/1.1/` and are loaded with the small resource helper in the test-fixture source set.

## Layout

| Path                           | Role                                                                                          |
|--------------------------------|-----------------------------------------------------------------------------------------------|
| `documents/*.json`             | Pretty-printed canonical wire fixtures                                                        |
| `documents/*.compact.json`     | Compact canonical wire fixtures for member-order-sensitive inputs                             |
| `negative/*.json`              | Read-only inputs for the negative corpus (malformed or context-invalid documents)             |
| `envelope-binding/*.json`      | Named typed-envelope binding-variant documents (stable names; not codec corpus entries)       |
| `patch/*.json`                 | Named PATCH request documents; one resource serves both low-level and typed PATCH tests wherever the request wire form is identical |

The corpus is passive and holds no manifest or registry files. Adapter tests name the resource files
they need directly and own diagnostics, locations, policies, decoded values, and other behavioral
expectations in their own specs. Resource filenames are the stable fixture identifiers, including
the named documents under `envelope-binding/` and `patch/`.

## Normative coverage

The corpus names representative wire inputs where cross-major drift would be costly. Filenames are
stable fixture identifiers; adapter specs own contexts, decoded values, diagnostics, and assertions.

| Group | Representative resources |
|-------|--------------------------|
| Unknown-member tolerance | `documents/unknown-members-tolerant.json` |
| Create identity | `documents/local-identifier.json`, `negative/unrelated-lid-linkage.json` |
| Endpoint role and linkage | `documents/relationship-null-with-related.json`, `documents/relationship-single-with-related.json`, `documents/relationship-collection-with-related.json`, `documents/relationship-collection-with-pagination.json`, `documents/resource-collection-with-pagination.json`, `negative/resource-with-related-link.json`, `negative/relationship-resource-object.json`, `negative/relationship-single-with-pagination.json` |
| Shape, identity, linkage, links | `documents/single-resource.json`, `documents/resource-collection.json`, `documents/single-identifier.json`, `documents/identifier-collection.json`, `documents/null-data.json`, `documents/meta-only.json`, `documents/empty-*.json`, `documents/relationship-*.json`, `documents/string-and-object-links.json`, `documents/errors-document.json`, `documents/jsonapi-object.json`, `documents/open-values.json`, `documents/extension-and-at-members.json`, `documents/member-order.json` |
| Compound linkage | `documents/compound-document.json`, `documents/compound-nested-intermediate.json`, `documents/compound-shared-identity.json`, `documents/compound-linked-article.json`, `documents/empty-included.json`, `negative/unlinked-included.json`, `negative/resource-with-pagination.json` |

## Regression rule

For a future bug whose observable behavior must match across Jackson 2 and Jackson 3, add or extend
one neutral corpus case where the wire scenario is major-independent, consume it from both adapter
suites, and keep any major-specific mechanic proof local.

## Usage

Tests should make their input, action, and expected result visible in the adapter spec. Small local
tables or helpers are appropriate when several documents exercise the same operation and assertion
shape; shared code must remain limited to input data, application-shaped fixture types, and resource
loading.

## Adding a fixture

1. Add the JSON document under the appropriate directory.
2. Update a local adapter spec with the new resource path and expected behavior.
3. Add a compact sibling only when exact member order matters.
4. If the document intentionally fails the pinned draft schema, document that reason in the
   adapter spec.
5. Run the adapter tests and the normal repository completion gates.

## Negative corpus

Read-only negative inputs live under `negative/`. Each adapter names the files it exercises and owns
the expected failure category, JSON pointer, validation rule code, source-location expectations, and
other diagnostics.

## Ambiguous primary data

Dual-success inputs whose decoded model depends on the explicit primary-data kind are ordinary
corpus documents. Each adapter names them directly and proves both readings locally with the
expected models.
