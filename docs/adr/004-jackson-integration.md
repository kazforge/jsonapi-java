# ADR-004: Configured Jackson Is the Mapping Authority

**Status:** Accepted  
**Date:** 2026-07-26  
## Context

An independent field/component/getter scanner would disagree with Jackson about logical properties, visibility, mix-ins, naming, ignored values, creators, and custom serializers. That would make the mapping surprising and invalidate the claim that it behaves like normal Jackson serialization.

Document envelopes such as links and metadata also do not have the default record wire shape.

## Decision

JSON:API annotations assign semantic roles. The caller-configured Jackson mapper owns property discovery, visibility, external naming, mix-ins, serializers/deserializers, creators, and other property mechanics. Adapter factories take a configured mapper instance and capability-specific collaborators, never mutate the caller's mapper, and may derive isolated internal mappers for capability-specific needs. Builder overloads that merely build a mapper are not a second construction model.

Use Jackson's introspection and logical property model for domain mapping. Do not establish independent field-first or getter-first discovery. Do not give JSON:API annotations a second member-name override; `@JsonApiAttribute` and `@JsonApiRelationship` are role markers only. A Jackson-visible property participates only when it has an appropriate JSON:API role, except for the conventional identifier: a Jackson-visible property whose configured Jackson external name is `id` is the sole intentional implicit JSON:API property-role convention. Otherwise-unclassified properties do not become attributes.

`@JsonApiResource(type = ...)` remains explicit JSON:API semantic data (the resource `type` member), not a Jackson property name. `@JsonApiRelationshipMeta(relationship = ...)` associates meta with a mapped relationship by that relationship's Jackson property identity; mapping then emits and reads the meta under the relationship's configured-Jackson external name.

Implement explicit codecs for JSON:API document structures, including:

- flat links and metadata;
- absent versus explicit-null data;
- sealed primary and relationship linkage;
- string and object links;
- additional members;
- construction and validation of decoded core documents before optional application binding.

Jackson ignores, names, mix-ins, serializers, and creator metadata remain authoritative for participating properties.
Capability contexts remain distinct: the neutral `ResourceTypeRegistry` registers wire resource types
against `java.lang.reflect.Type`, which the consuming adapter resolves with its configured mapper.
Jackson 2 and Jackson 3 have semantic capability parity through native mapper types, not identical
convenience-overload inventories.

## Consequences

- Domain mapping follows familiar Jackson behavior for naming, visibility, mix-ins, and conversion.
- There is exactly one authority for property names and conversion: configured Jackson.
- Record annotation propagation is resolved as one logical property.
- The Jackson module is more than a default record serializer.
- Supporting a Jackson feature means proving it with an integration test, not assuming reflection preserves it.
