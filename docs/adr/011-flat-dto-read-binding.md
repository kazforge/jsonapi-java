# ADR-011: Flat DTO Reads Remain Document-First

**Status:** Accepted  
**Date:** 2026-07-30  
## Context

ADR-006 established validated document-model reads as the first deserialization boundary and
deferred arbitrary domain graph hydration. That boundary protects wire semantics, but requiring
Jackson or Spring users to manipulate `JsonApiDocument` for routine flat request and response DTOs
would make the optional mapping adapters incomplete.

Flat resource binding does not require graph hydration. A resource object's identifier,
attributes, and relationship linkage can be mapped through Jackson's logical property model while
`included` remains a separate heterogeneous collection.

## Decision

All JSON input is decoded into and validated as the core document model before domain binding.
Document-model reads remain a supported public API, but common Jackson and Spring DTO flows use a
domain-facing typed envelope rather than exposing `JsonApiDocument` in application signatures.

Flat DTO binding:

- uses the same Jackson logical-property metadata, annotations, creator rules, converters, and
  diagnostics in both directions;
- participates only for properties assigned a JSON:API role (plus the conventional identifier
  whose configured Jackson external name is `id`); unannotated ordinary properties are not
  bound as attributes;
- uses Jackson's effective **deserialization** property model to decide which supplied mapped
  members can participate in ordinary reads; a serialization accessor is not proof of read
  bindability;
- supports normal readable/writable properties, setter-only properties, creator-only or
  constructor-bound properties, and Jackson write-only/deserialization-only properties;
- rejects a supplied member mapped to a getter-only, read-only, or otherwise non-deserializable
  property with a stable mapping diagnostic at the JSON:API wire location rather than silently
  discarding the value;
- preserves single, collection, explicit-null, and absent primary-data states in the typed
  envelope rather than guessing cardinality from a Java target;
- binds `@JsonApiRelationship` properties to explicit null, single, or collection linkage only;
- never resolves relationship properties from `included`;
- binds included resources independently through an explicit resource-type-to-Java-type registry;
- fails with a stable mapping diagnostic when typed included binding encounters an unregistered
  resource type; and
- preserves document-level links, metadata, JSON:API information, errors, and valid additional
  members through the envelope without reflectively serializing core records.

Automatic graph assembly, persistence lookup, identity-map mutation, and cycle resolution remain
out of scope. Presence-aware resource updates are governed separately because partial-update
construction has different semantics from complete DTO binding.

## Consequences

- Applications using documented flat DTO shapes need not depend directly on core model types in
  routine Jackson or Spring entry points.
- Ordinary flat-read DTO directionality is explicit and portable: adapter implementations may use
  Jackson-major-specific introspection APIs, but supplied values must obey the effective
  deserialization model described above.
- Validation and absent-versus-null correctness remain centralized in core.
- Included resources are available as independently bound DTOs but do not silently change domain
  relationships.
- Applications must register every included resource type they ask the typed envelope to bind.
- Full graph hydration would require a later ADR and cannot be introduced as a convenience default.
