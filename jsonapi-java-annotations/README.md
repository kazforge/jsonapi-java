# jsonapi-java-annotations

Runtime-visible, dependency-free annotations that declare JSON:API resource, identifier, local-identifier, attribute, relationship, resource-meta, and relationship-meta **roles** on domain classes and records for later Jackson mapping.

## Packages

| Package                                | Role                                                                    |
|----------------------------------------|-------------------------------------------------------------------------|
| `com.kazforge.jsonapi.annotation` | Public annotation API (`@JsonApiResource`, id, local id, attribute, relationship, meta, relationship meta) |

## Minimal usage

```java
@JsonApiResource(type = "articles")
public record Article(
    @JsonApiId String id,
    @JsonApiLocalId String localId,
    @JsonApiAttribute String title,
    @JsonApiRelationship String writtenBy,
    @JsonApiMeta ArticleMeta meta,
    @JsonApiRelationshipMeta(relationship = "writtenBy") AuthorMeta authorMeta) {}
```

These annotations assign semantic roles only. Configured Jackson owns property discovery, visibility, external/wire naming, mix-ins, creators, and value conversion. Jackson mapping, member-name validation, identifier conversion, and inclusion policy belong in [`jsonapi-java-jackson3`](../jsonapi-java-jackson3/README.md). Per-linkage identifier meta is an opt-in `RelationshipLinkage` value in [`jsonapi-java-jackson-api`](../jsonapi-java-jackson-api/README.md), not an annotation.

`@JsonApiResource(type = "articles")` is explicit JSON:API semantic data (the resource `type` member), not a Jackson property name.

`@JsonApiId` and `@JsonApiLocalId` are independent identity roles: `@JsonApiId` maps only the JSON:API `id` member and `@JsonApiLocalId` maps only the `lid` member. Neither role falls back to the other, and one logical property must not claim both. A domain type may declare either role alone or both together. The local identifier is a JSON:API protocol concept for resources identified only within their document (for example client-generated identifiers in creation requests), not an application persistence or transient-entity heuristic.

## Non-goals

This module does not provide Jackson codecs, document model types, inclusion/fetch/cascade policy, query parsing, or framework adapters. See [ADR-007](../docs/adr/007-module-boundaries.md).

## Further reading

- [Architecture overview](../docs/architecture.md)
- [Conformance checklist](../docs/conformance.md) — annotation metadata
- [ADR-004 — Jackson introspection](../docs/adr/004-jackson-integration.md)
- [ADR-005 — Linkage vs inclusion](../docs/adr/005-domain-mapping-and-inclusion.md)
- [ADR-007 — Module boundaries](../docs/adr/007-module-boundaries.md)
- [ADR-021 — KazForge namespace](../docs/adr/021-kazforge-namespace.md)
- [ADR-009 — JSpecify nullness](../docs/adr/009-jspecify-nullness.md)
- [ADR-010 — Architectural tests](../docs/adr/010-architectural-tests.md)
- [ADR-015 — Flat whole-object mapping for resource-side meta](../docs/adr/015-flat-whole-object-meta-mapping.md)
- [ADR-017 — Opt-in RelationshipLinkage for resource identifier meta](../docs/adr/017-resource-identifier-meta-mapping.md)
- [Root agent workflow](../AGENTS.md)

## For contributors / agents

- **Role-only:** Annotations assign JSON:API semantic roles. They do not invent a second property model, name JSON:API members, request inclusion, or carry converters/persistence/query elements. Configured Jackson is the sole property-name authority (ADR-004).
- **Explicit participation:** A Jackson-visible property participates only through a JSON:API role annotation, except the conventional identifier whose configured Jackson external name is `id`. Otherwise-unclassified properties do not become attributes. There is no conventional `lid` property name; local identifiers are explicit `@JsonApiLocalId` declarations.
- **Identity roles:** `@JsonApiId` maps only `id`; `@JsonApiLocalId` maps only `lid`. At most one property per identity role, never both roles on one property, and at least one identity role per mapped type. JSON:API `id`, JSON:API `lid`, and application/database identity are distinct concepts; mapping never infers one from another.
- **Meta roles:** `@JsonApiMeta` maps the complete resource-side `meta` object to one application-owned property; `@JsonApiRelationshipMeta(relationship)` maps the complete `meta` object of a specific mapped relationship and identifies that relationship by Jackson property identity. Mapping then uses the relationship's configured-Jackson external name on the wire. Per-linkage `ResourceIdentifier.meta` is an opt-in `RelationshipLinkage<T, M>` value, not an annotation (ADR-017). At most one resource-meta or relationship-meta property per location.
- **Nullness:** The production package is `@NullMarked` (JSpecify compile-only). Annotation `String` elements are non-null. Groovy tests are not annotated.
- **Architectural tests:** This dependency-free module relies on the compiler and its declared classpath for its dependency boundary. ArchUnit protects the cross-module boundaries where the compiler cannot express the invariant (ADR-010).
- **Tests:** `AnnotationMetaContractSpec` under `src/test/groovy/` directly owns the runtime annotation metadata contract without Jackson fixtures.
