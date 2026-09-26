# ADR-006: Document-First Deserialization

**Status:** Accepted  
**Date:** 2026-07-26  
## Context

Resource linkage may reference a resource not present in `included`. Arbitrary domain graph hydration also requires identity handling, cycle policy, constructor selection, partial-update semantics, and application-specific resolution.

Treating graph hydration as the inverse of serialization would hide these unresolved choices.

## Decision

Document reads decode JSON into the core document model and validate it before optional application
binding. Applications may consume resource objects, identifiers, relationships, and errors directly.
Ordinary flat DTO binding uses Jackson's effective deserialization property model: only mapped,
deserializable members participate (plus the conventional `id` property). A supplied mapped member
without an effective deserialization target fails at its JSON:API wire location rather than being
silently discarded. Relationship properties bind linkage, never hydrated `included` resources.

Typed envelopes preserve primary-data shape and bind included resources independently through an
explicit resource-type registry; an unregistered included type fails rather than being guessed.
Neither path resolves arbitrary annotated domain graphs or performs persistence lookup, identity-map
mutation, or cycle resolution. Presence-aware updates remain separate from complete DTO binding
([ADR-011](011-resource-patch-binding.md)).

The codec remains capable of reading request and response document shapes; this decision limits the target Java representation, not JSON:API wire coverage.

## Consequences

- Deserialization has a clear, achievable contract.
- Linkage-only documents do not fabricate domain instances.
- Flat DTO reads remain useful without turning `included` into an implicit graph-binding policy.
- Users wanting graph binding still need policy-aware application mapping.
