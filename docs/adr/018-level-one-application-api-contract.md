# ADR-018: Major-Neutral Level-1 Application API Contract

**Status:** Accepted
**Date:** 2026-09-04

## Context

The major-specific capability APIs provide explicit codec, mapping, binding, typed-envelope, and
PATCH mechanisms. Ordinary application and framework code should not have to coordinate those
pipeline phases, and framework contracts should not depend on either Jackson major.

## Decision

`jsonapi-java-api` owns a narrow neutral operation contract in
`com.kazforge.jsonapi.api`. Its `JsonApi` root exposes four facets:

- resources: strict homogeneous resource reads, single/collection writes, typed document results,
  and create/update document authoring;
- relationships: strict to-one, explicit-null, and to-many linkage documents;
- documents: raw validated document operations with explicit semantic read context where inference
  would be unsafe;
- patches: conventional typed `PatchPresence<T>` binding and the explicit lower-level
  `PatchCommand<T>` projection.

Both Jackson adapters implement this contract with native-major runtimes. Neutral signatures contain
no mapper, parser, generator, serializer, deserializer, Jackson type model, runtime-major detection,
Spring type, or HTTP policy.

Level 1 coordinates common operations while advanced major-specific APIs remain public for explicit
mechanism and control, including parameterized Jackson types, heterogeneous registry-backed
envelopes, capability-specific contexts, and raw mapping or codec composition. The neutral facade
never guesses an ambiguous document shape or resource target.

The contract preserves these architectural boundaries:

- configured Jackson owns property discovery, names, visibility, mix-ins, creators, and ordinary
  Java conversion;
- `id` and `lid` are separate protocol roles with no fallback or persistence interpretation;
- ordinary relationships follow [ADR-017](017-relationship-data-presence-in-domain-mapping.md):
  emitted mapped relationships contain linkage, while links-only and meta-only forms stay advanced;
- decoration only adds links to existing mapped resources and relationships;
- representation selection is operation-scoped, policy is runtime/application-scoped, and
  sparse-fieldset provenance remains on `MappedDocument` for the writer to consume;
- create/update authoring delegates to core validation rather than restating its rules;
- typed and low-level PATCH operate on validated update documents, do not read `included`, and take
  identity from `id`, not `lid`;
- validation, document-read, and mapping exception families remain distinct; the facade introduces
  no unified exception model.

## Consequences

- Ordinary callers use four cohesive operation groups instead of manually coordinating internal
  phases, and framework adapters gain a Jackson-major-neutral seam.
- The neutral API is intentionally inexpressive where inference is unsafe. Major-specific
  `JavaType` write/mapping control, generic typed-document envelopes, heterogeneous binding,
  relationship top-level members, and mechanism-level policy remain advanced.
- Relationship helpers are linkage-only and cardinality-strict; they do not become graph hydration or
  general relationship-envelope APIs.
- Focused Javadoc owns exact method, option/result, and diagnostic contracts; the
  `jsonapi-java-api` README provides capability, usage, and navigation context.
