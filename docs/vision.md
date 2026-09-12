# Vision and Architectural Strategy: `jsonapi-java`

> Make JSON:API v1.1 documents straightforward to read and write in Java without taking ownership of an application's persistence, endpoints, or business architecture.

This document is stable product direction and principles. It does not describe what currently
exists; current capability lives in the [root module registry](../README.md) and each module
README. The current cross-module mental model lives in [`docs/architecture.md`](architecture.md).
Future work coordination, when used, lives in an external coordinating layer—not in this
repository. Authority and owner/reference rules are in [`AGENTS.md`](../AGENTS.md).

## Product boundary

`jsonapi-java` is a lightweight, Java 21+ document codec with optional bidirectional
domain-mapping, query-parameter, and web-framework adapters.

The library owns:

- an immutable, dependency-free representation of JSON:API documents;
- strict validation of document structure and cross-document invariants;
- Jackson encoding and decoding of that document model;
- opt-in mapping between ordinary Jackson-visible POJOs/records and flat resource objects through
  domain-facing document envelopes;
- presence-aware resource-update commands that preserve omitted values versus explicit JSON
  `null` without mutating application objects;
- optional parsing of JSON:API query parameters;
- optional Spring integration for the JSON:API media type.

Applications continue to own:

- controller and endpoint design;
- persistence, repositories, transactions, and authorization;
- filtering, sorting, pagination, and query execution;
- decisions about supported include paths, fields, profiles, and extensions;
- authorization and application of resource-update commands to existing domain state;
- HTTP operation semantics beyond behavior explicitly implemented by an adapter.

This boundary is deliberate. The project is not an API engine, ORM bridge, repository abstraction, or endpoint generator.

## What “lightweight” means

- `jsonapi-java-core` has no third-party runtime dependencies. A compile-only JSpecify
  annotation jar may be used for nullness metadata (see ADR-009); it is not a functional
  runtime dependency and must not appear on the published runtime classpath.
- Domain mapping is opt-in, bidirectional for documented flat DTO shapes, and requires no
  inheritance or framework interfaces.
- Jackson, query parsing, and Spring WebMVC are separate artifacts.
- A relationship creates linkage; it does not automatically traverse and include an object graph.
- The library does not execute filters, sorts, or pagination strategies.
- Framework adapters remain thin and do not introduce application architecture.

“Lightweight” does not mean omitting required JSON:API semantics. Presence versus explicit `null`, compound-document linkage, link forms, extension members, and media-type behavior must remain representable and testable.

## Design principles

### Wire semantics before Java convenience

The document model preserves distinctions visible on the wire, including an absent member versus a member whose value is `null`. Public types are designed from official JSON:API examples and negative cases, not from a desired type count.

### Strict documents, explicit application policy

Local invariants are enforced when values are constructed. Invariants involving a whole document, such as included-resource uniqueness and full linkage, are enforced by document validation. Inclusion, sparse fieldsets, traversal limits, and supported query features are explicit policies rather than hidden defaults.

### Jackson is authoritative for Java properties

Domain mapping uses Jackson's logical property model. It preserves Jackson visibility, naming, mix-ins, ignored properties, custom serializers, and creator rules instead of independently reflecting over fields and getters.

### Document-first correctness, DTO-first adapters

Deserialization first produces and validates the JSON:API document model. Jackson and framework
adapters may then bind primary resources to annotated flat DTOs and expose document-level members
through a typed domain envelope, so routine application code need not manipulate the core model.

Relationship properties bind resource linkage only. Included resources are bound independently
through explicit resource-type registration and are never injected into relationship properties.
Automatic reconstruction of arbitrary domain graphs remains outside the product boundary because
unresolved linkage, cycles, identity, and persistence resolution require application policy.

Resource PATCH binding produces a presence-aware update command. Omitted attributes and
relationships remain distinguishable from explicit attribute `null` and explicit null, single, or
collection linkage. Applications authorize and apply that command; the library does not mutate an
existing DTO or persistence object. A direct typed PATCH DTO path is also available: applications
can declare an annotated PATCH DTO whose patchable members are `PatchPresence<T>` and bind a
validated update document straight into it, keeping the same presence tri-state without a
`PatchCommand`-to-DTO projector.

### Extensible without interpreting extensions

The base model preserves valid extension members and `@` members. It does not implement extension-specific semantics unless a separate feature explicitly declares support.

## Modules

Optional adapters are separate artifacts ([ADR-007](adr/007-module-boundaries.md)). Maven group is
`com.kazforge`; Java packages live under `com.kazforge.jsonapi` (see
[ADR-021](adr/021-kazforge-namespace.md)).

Current inventory and capability live in the [root README](../README.md) and each module README.
Planned modules appear in that registry; they have no usable entry points until implemented.

## Compliance contract

Compliance is tracked by feature and layer instead of claimed globally. Current status is
[`docs/conformance.md`](conformance.md). When a capability is implemented, that checklist is
updated with one of: supported, pass-through, delegated, deferred, or out of scope.

### Guaranteed by the core and codec when implemented

- JSON:API v1.1 base document structures and legal wire forms.
- Required local and aggregate document invariants.
- Correct distinction between absent, explicit `null`, single, and collection data.
- Parsing and emission of standard links, errors, metadata, resource linkage, and compound documents.
- Preservation of valid extension and `@` members without interpreting their semantics.

### Guaranteed only by optional adapters

- Query-family parsing is guaranteed by `jsonapi-java-query`; support and execution remain application choices.
- JSON:API `Content-Type` and `Accept` handling is guaranteed only by the applicable web adapter.
- Domain mapping, typed envelopes, and PATCH commands are guaranteed only for documented Jackson
  property shapes and mapping policies.

### Application responsibilities

- Endpoint availability and operation semantics.
- HTTP status selection outside adapter-defined behavior.
- Persistence and relationship mutation.
- Authorization and application of presence-aware update commands.
- Authorization and visibility of fields and relationships.
- Query execution and limits.
- Extension and profile semantics.

## Initial non-goals

- Generated controllers or repositories.
- ORM-specific behavior or automatic lazy association traversal.
- Automatic domain graph hydration or injection of `included` resources into relationships.
- Automatic application of PATCH commands to domain or persistence objects.
- A filtering or pagination DSL.
- Relationship endpoint implementation.
- Extension-specific processing beyond explicitly supported extensions.
- A guarantee that an application using the library is globally JSON:API compliant.
