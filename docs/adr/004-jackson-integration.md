# ADR-004: Jackson Introspection Is Authoritative

**Status:** Accepted  
**Date:** 2026-07-26  
## Context

An independent field/component/getter scanner would disagree with Jackson about logical properties, visibility, mix-ins, naming, ignored values, creators, and custom serializers. That would make the mapping surprising and invalidate the claim that it behaves like normal Jackson serialization.

Document envelopes such as links and metadata also do not have the default record wire shape.

## Decision

JSON:API annotations assign semantic roles. Configured Jackson owns property discovery, visibility, external naming, mix-ins, serializers/deserializers, creators, and other property mechanics.

Use Jackson's introspection and logical property model for domain mapping. Do not establish independent field-first or getter-first discovery. Do not give JSON:API annotations a second member-name override; `@JsonApiAttribute` and `@JsonApiRelationship` are role markers only. A Jackson-visible property participates only when it has an appropriate JSON:API role, except for the conventional identifier: a Jackson-visible property whose configured Jackson external name is `id` is the sole intentional implicit JSON:API property-role convention. Otherwise-unclassified properties do not become attributes.

`@JsonApiResource(type = ...)` remains explicit JSON:API semantic data (the resource `type` member), not a Jackson property name. `@JsonApiRelationshipMeta(relationship = ...)` associates meta with a mapped relationship by that relationship's Jackson property identity; mapping then emits and reads the meta under the relationship's configured-Jackson external name.

Implement explicit codecs for JSON:API document structures, including:

- flat links and metadata;
- absent versus explicit-null data;
- sealed primary and relationship linkage;
- string and object links;
- additional members;
- strict validation during reads.

Jackson ignores, names, mix-ins, serializers, and creator metadata remain authoritative for participating properties.

## Consequences

- Domain mapping follows familiar Jackson behavior for naming, visibility, mix-ins, and conversion.
- There is exactly one authority for property names: configured Jackson.
- Record annotation propagation is resolved as one logical property.
- The Jackson module is more than a default record serializer.
- Exact wire fixtures are required before the core API is considered stable.
- Supporting a Jackson feature means proving it with an integration test, not assuming reflection preserves it.
