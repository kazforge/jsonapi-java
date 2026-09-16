# Architecture Decision Records

ADRs record consequential, hard-to-reverse “why” decisions. The current cross-module mental model
lives in [`docs/architecture.md`](../architecture.md). Stable product direction lives in
[`docs/vision.md`](../vision.md); current capability lives in module READMEs and
[`docs/conformance.md`](../conformance.md). Owner and conflict rules are in
[`AGENTS.md`](../../AGENTS.md).

- [ADR-001: Document Codec as the Product Boundary](001-product-boundary.md)
- [ADR-002: Preserve JSON:API Wire States](002-document-representation.md)
- [ADR-003: Strict Construction and Aggregate Validation](003-validation-and-immutability.md)
- [ADR-004: Jackson Introspection Is Authoritative](004-jackson-integration.md)
- [ADR-005: Separate Linkage from Inclusion](005-domain-mapping-and-inclusion.md)
- [ADR-006: Document-First Deserialization](006-read-boundary.md)
- [ADR-007: Optional Adapter Modules](007-module-boundaries.md)
- [ADR-009: JSpecify Nullness](009-jspecify-nullness.md)
- [ADR-010: Architectural Tests for Module Boundaries](010-architectural-tests.md)
- [ADR-011: Flat DTO Reads Remain Document-First](011-flat-dto-read-binding.md)
- [ADR-012: Resource PATCH Produces Presence-Aware Commands](012-resource-patch-binding.md)
- [ADR-013: Direct Typed PATCH DTO Binding](013-direct-typed-patch-dto-binding.md)
- [ADR-014: Recursive Structured Value PATCH Semantics](014-recursive-structured-value-patch-semantics.md)
- [ADR-015: Flat Whole-Object Mapping for Resource-Side Meta](015-flat-whole-object-meta-mapping.md)
- [ADR-016: Mapper-Instance Construction for Jackson Adapters](016-jackson-adapter-construction.md)
- [ADR-017: Opt-in RelationshipLinkage for Resource Identifier Meta](017-resource-identifier-meta-mapping.md)
- [ADR-018: Ordinary Domain Relationships Remain Linkage-Oriented](018-relationship-data-presence-in-domain-mapping.md)
- [ADR-019: Major-Neutral Level-1 Application API Contract](019-level-one-application-api-contract.md)
- [ADR-020: Jackson-Neutral Implementation Helpers](020-jackson-neutral-implementation-helpers.md)
- [ADR-021: KazForge Namespace and Maven Group](021-kazforge-namespace.md)
