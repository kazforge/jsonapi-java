# jsonapi-java-core

Dependency-free Java representation of [JSON:API v1.1](https://jsonapi.org/) documents, with local
construction invariants and aggregate document validation.

## Packages and entry points

| Package | Responsibility |
|---------|----------------|
| [`com.kazforge.jsonapi.core.model`](src/main/java/com/kazforge/jsonapi/core/model/package-info.java) | Immutable documents, resources, relationships, links, errors, and wire-state variants |
| [`com.kazforge.jsonapi.core.validation`](src/main/java/com/kazforge/jsonapi/core/validation/package-info.java) | Stable diagnostics, member grammar, and model-independent policy values |
| [`com.kazforge.jsonapi.core.aggregate`](src/main/java/com/kazforge/jsonapi/core/aggregate/package-info.java) | [`JsonApiDocumentValidator`](src/main/java/com/kazforge/jsonapi/core/aggregate/JsonApiDocumentValidator.java) and [`ValidationContext`](src/main/java/com/kazforge/jsonapi/core/aggregate/ValidationContext.java) |
| [`com.kazforge.jsonapi.core.internal`](src/main/java/com/kazforge/jsonapi/core/internal/package-info.java) | Shared implementation helpers; unsupported API |

## Usage

```java
ResourceObject resource = ResourceObject.of("articles", "1");
JsonApiDocument document = JsonApiDocument.withData(
    new DocumentData.SingleResource(resource));

new JsonApiDocumentValidator().validate(document, ValidationContext.defaults());
```

Construct model values first; their constructors enforce invariants that need only the value being
created. Run `JsonApiDocumentValidator` for identity uniqueness, full linkage, document usage,
endpoint role, link context, and extension/profile policy. The
[conformance checklist](../docs/conformance.md) owns the current rule inventory.

Java `null` on a containing component means that a member is absent. Sealed model variants represent
explicit JSON `null`, single, and collection forms. Present-empty wrappers and collections remain
distinct from absence. `ErrorObject.builder()` and `ErrorSource.builder()` construct the same
immutable core values as direct constructors.

## Boundaries

- Core has no functional third-party runtime dependency; JSpecify is compile-only metadata.
- It provides no Jackson codec or mapping, HTTP adapter, query parser, persistence integration, or
  extension-specific semantics.
- Valid extension and `@` members are preserved without interpretation.
- Validation failures use `JsonApiValidationException`, a stable `ValidationRuleCode`, and a
  JSON Pointer-like path.
- The package dependency direction is aggregate → model/internal/validation, model →
  internal/validation, and internal → validation; reverse edges are forbidden.

See the [architecture overview](../docs/architecture.md),
[ADR-002](../docs/adr/002-document-representation.md), and
[ADR-003](../docs/adr/003-validation-and-immutability.md).
