# KAZ-137 PoC — mapper-neutral compound inclusion

This branch is an exploratory proof of concept only. Linear KAZ-137 remains in Backlog.

## Hypothesis

JSON:API mapping semantics should not be duplicated per JSON mapper backend. Jackson 2 and Jackson 3 should provide mapper-specific mechanics, while jsonapi-java owns the mapping domain.

A useful abstraction must pass two tests:

1. it still makes sense in a future Jackson-3-only world; and
2. a hypothetical non-Jackson backend could implement it without exposing Jackson concepts under neutral names.

## What this PoC changes

The existing Jackson 2 and Jackson 3 `CompoundInclusionEngine` implementations were nearly identical (~18 KB each).

The PoC moves the inclusion traversal semantics into one generic, Jackson-free implementation:

- `GenericCompoundInclusionEngine<T>`
- `InclusionMappingBackend<T>`

Each Jackson major now supplies only a backend bridge that keeps `JavaType`, property introspection, Optional/collection mechanics, and configured domain rendering inside its own adapter.

The existing `CompoundInclusionEngine` public/internal construction surface remains as a thin facade so callers do not need to change for the experiment.

## Boundary demonstrated

```text
Domain objects
    |
    v
InclusionMappingBackend<T>
    |  mapper-specific:
    |  type token / property lookup / value access
    v
GenericCompoundInclusionEngine<T>
    |  jsonapi-java semantics:
    |  include path traversal
    |  include policy
    |  identity/visit handling
    |  sparse-fieldset edge handling
    |  included ordering/dedup
    v
JSON:API core ResourceObject
```

The shared engine has no Jackson imports.

A focused test uses a fake backend with a custom `FakeType`, proving that the traversal does not require Jackson `JavaType` or Jackson introspection.

## Rough code-size signal

Before:

- Jackson 2 `CompoundInclusionEngine`: ~18.2 KB
- Jackson 3 `CompoundInclusionEngine`: ~18.2 KB
- combined duplicated concern: ~36.4 KB

PoC:

- shared engine: ~12.7 KB
- shared backend contract: ~1.9 KB
- Jackson 2 backend bridge: ~5.7 KB
- Jackson 3 backend bridge: ~5.7 KB
- two thin compatibility facades: ~1.9 KB each
- combined: ~29.8 KB

The immediate source reduction is therefore modest (~6.6 KB / ~18%), but this is not the main result. The JSON:API inclusion algorithm now exists once, and the remaining duplication is concentrated in mapper mechanics. That remaining duplication is exactly what a broader neutral mapping model/backend SPI would need to evaluate.

## Important limitation

The PoC now uses a dedicated internal `jsonapi-java-mapping` module. Jackson 2 and Jackson 3 depend on it through:

```kotlin
implementation(project(":jsonapi-java-mapping"))
```

The module is published only because the Jackson artifacts need it transitively at runtime; it is not intended as a user-facing artifact or compatibility surface. Its implementation lives under `com.kazforge.jsonapi.mapping.internal`.

The module boundary is enforced by architecture tests: the mapping domain may not depend on Jackson 2, Jackson 3, or their concrete adapter packages, and supported Jackson public signatures may not expose mapping-internal types.

One transitional dependency remains deliberately visible: mapper-neutral representation and diagnostic contracts such as `EffectiveRepresentation`, include policies, and `JsonApiMappingException` currently live in `jsonapi-java-jackson-api`. The PoC therefore has `jsonapi-java-mapping -> jsonapi-java-jackson-api`. A production design must decide whether those contracts actually belong in a mapper-neutral API/module before a non-Jackson backend could be considered cleanly supported.

The branch now also adds a representative black-box mapping contract shared by Jackson 2 and
Jackson 3. The contract freezes observable behavior for resource type, id, scalar attributes,
to-one/to-many linkage, and compound inclusion without freezing a proposed mapper SPI.

A separate test-only `jsonapi-java-gson-poc` module runs the same contract using Gson 2.14.0 and
the shared inclusion engine. It additionally proves that Gson-specific `@SerializedName` handling
can remain a backend concern. The module has no production sources and no publish plugin; it is an
architecture fitness test only.

If this direction survives further evaluation, likely follow-up questions are:

- whether mapper-neutral representation/diagnostic contracts should move out of `jsonapi-java-jackson-api`;
- whether `ResourceMapping` / property metadata can become a neutral mapping definition produced by mapper-specific introspection;
- how much of `DomainResourceWriter`, PATCH binding, relationship linkage, and structured value binding can then consume that neutral definition;
- whether tree-based wire decoding independently simplifies the document codec.

## What this PoC intentionally does not do

- no claim that the new module boundary is production-ready;
- no production Gson module or Gson support commitment;
- no broad rewrite of `DomainResourceWriter`;
- no change to the public API;
- no Linear state change;
- no claim that the backend contract shown here is final.

The useful result to judge is the architectural shape: mapper-specific mechanics can sit behind an opaque type token while JSON:API traversal semantics remain shared and independently meaningful.


## Result document

The consolidated architectural findings, limitations, Gson assessment and proposed production work
are recorded in `docs/spikes/kaz-137-poc-result.md`.
