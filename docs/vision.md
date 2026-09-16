# Vision and Architectural Strategy: `jsonapi-java`

> Make JSON:API v1.1 documents straightforward to read and write in Java without taking ownership of an application's persistence, endpoints, or business architecture.

This document owns stable product direction. Current modules and capability live in the
[root registry](../README.md), module READMEs, [architecture snapshot](architecture.md), and
[conformance checklist](conformance.md).

## Product boundary

`jsonapi-java` is a lightweight Java document library with optional domain-mapping, query-parsing,
and framework integrations. The library owns:

- an immutable representation of JSON:API documents;
- local and whole-document validation;
- opt-in encoding, decoding, and flat application-value mapping;
- presence-aware update projections that do not mutate application state;
- optional parsing of standardized query selections;
- thin optional framework integration for JSON:API transport concerns.

Applications continue to own endpoints, persistence, transactions, authorization, query execution,
relationship resolution, supported fields/includes/profiles/extensions, business validation, and
application of updates. The project is not an API engine, ORM bridge, repository abstraction, or
endpoint generator.

## Design principles

### Wire semantics before Java convenience

The model preserves distinctions visible on the wire, including absence, explicit `null`, present
empty values, and linkage cardinality. Public contracts follow JSON:API semantics rather than
minimizing the number of Java types.

### Strict documents, explicit policy

Local invariants are enforced during value construction; rules involving a whole document are
enforced by aggregate validation. Inclusion, sparse fieldsets, traversal limits, supported query
features, and authorization are explicit policy rather than hidden defaults.

### Configured Jackson authority

Domain mapping follows configured Jackson property discovery, visibility, names, mix-ins, creators,
serializers, and deserializers. JSON:API annotations assign semantic roles; they do not create a
second Java-property model.

### Document-first reads, application-owned graphs

Deserialization first produces and validates the JSON:API document model. Optional adapters may then
bind flat application shapes. Relationships remain linkage-oriented, and included resources are not
automatically injected into relationship properties. Reconstructing arbitrary graphs requires
identity, persistence, loading, and authorization policy that belongs to the application.

### Presence-aware updates without mutation

Update binding preserves omitted members versus explicit null and supplied values. The library
validates and projects requested changes; applications authorize and apply them to domain or
persistence state.

### Extensible without interpreting extensions

Valid extension and `@` members remain representable without the base library claiming their
semantics. Extension-specific behavior requires an explicitly scoped feature.

## Module strategy

- Core remains free of functional third-party runtime dependencies; compile-only nullness metadata
  is allowed.
- Domain mapping is opt-in and requires no inheritance or framework interface.
- Jackson majors, query parsing, and framework integrations remain separate artifacts.
- Native-major adapters share neutral semantic contracts without runtime detection or a
  lowest-common-denominator Jackson abstraction.
- Framework adapters stay thin and depend on lower-layer public contracts, never the reverse.
- Planned modules have no usable entry point until they join the build. `settings.gradle.kts` owns
  current membership; the root README provides its human-readable inventory.

## Compliance philosophy

Compliance is reported by feature and layer, not claimed globally. Core and codec guarantees,
optional-adapter behavior, delegated application policy, deferred work, and out-of-scope concerns
remain distinguishable. Supplemental schema checks never replace the textual JSON:API specification
or the feature statuses in [`docs/conformance.md`](conformance.md).

## Non-goals

- Generated controllers, endpoints, or repositories.
- ORM-specific behavior or automatic lazy-association traversal.
- Automatic domain-graph hydration.
- Automatic application of update projections.
- Query execution, filtering, sorting, or pagination strategies.
- Relationship endpoint implementation.
- Unscoped interpretation of extension or profile semantics.
- A guarantee that an application using the library is globally JSON:API compliant.
