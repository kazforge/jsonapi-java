# JSON:API 1.1 document fixtures

Version-neutral JSON:API documents used by adapter tests. Jackson 2 and Jackson 3 tests share this
corpus; do not fork major-specific copies. The files ship as classpath resources under
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
