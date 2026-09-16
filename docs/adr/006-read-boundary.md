# ADR-006: Document-First Deserialization

**Status:** Accepted  
**Date:** 2026-07-26  
## Context

Resource linkage may reference a resource not present in `included`. Arbitrary domain graph hydration also requires identity handling, cycle policy, constructor selection, partial-update semantics, and application-specific resolution.

Treating graph hydration as the inverse of serialization would hide these unresolved choices.

## Decision

Initial read support decodes JSON into the document model and validates it. It does not automatically hydrate annotated domain object graphs.

Applications may consume resource objects, identifiers, relationships, and errors directly.
[ADR-010](010-flat-dto-read-binding.md) adds document-first flat DTO binding and independently
bound included resources without graph hydration. [ADR-011](011-resource-patch-binding.md) adds
presence-aware update commands without applying them to domain state.

The codec remains capable of reading request and response document shapes; this decision limits the target Java representation, not JSON:API wire coverage.

## Consequences

- Deserialization has a clear, achievable contract.
- Linkage-only documents do not fabricate domain instances.
- Request validation can ship before a domain hydration system.
- Users wanting graph binding still need policy-aware application mapping.
- Flat DTO serialization/deserialization may be symmetric while graph hydration remains excluded.
