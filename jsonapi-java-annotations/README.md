# jsonapi-java-annotations

Runtime-visible, dependency-free annotations that assign JSON:API semantic roles to domain classes,
records, and properties for later adapter mapping. User examples belong to
[Resources](https://jsonapi.kazforge.com/resources/).

The public API is the
[`com.kazforge.jsonapi.annotation`](src/main/java/com/kazforge/jsonapi/annotation/package-info.java)
package: [`@JsonApiResource`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiResource.java),
[`@JsonApiId`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiId.java),
[`@JsonApiLocalId`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiLocalId.java),
[`@JsonApiAttribute`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiAttribute.java),
[`@JsonApiRelationship`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiRelationship.java),
[`@JsonApiMeta`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiMeta.java), and
[`@JsonApiRelationshipMeta`](src/main/java/com/kazforge/jsonapi/annotation/JsonApiRelationshipMeta.java).

Annotations assign roles only. Configured Jackson owns property discovery, visibility, external
names, mix-ins, creators, and conversion. `@JsonApiResource.type()` is JSON:API semantic data rather
than a Java property name.

`id` and `lid` are independent roles and never fall back to one another; there is no conventional
implicit `lid`. Resource meta, relationship meta, and per-linkage identifier meta are distinct.
Identifier meta uses the neutral `RelationshipLinkage<T, M>` value rather than another annotation.

This module provides no codec, document model, inclusion/fetch/cascade policy, query parser, or
framework integration. See the [architecture overview](../docs/architecture.md),
[ADR-004](../docs/adr/004-jackson-integration.md), and
[ADR-011](../docs/adr/011-flat-whole-object-meta-mapping.md).
