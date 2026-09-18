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
- [ADR-008: JSpecify Nullness](008-jspecify-nullness.md)
- [ADR-009: Architectural Tests for Module Boundaries](009-architectural-tests.md)
- [ADR-010: Flat DTO Reads Remain Document-First](010-flat-dto-read-binding.md)
- [ADR-011: Resource PATCH Produces Presence-Aware Commands](011-resource-patch-binding.md)
- [ADR-012: Direct Typed PATCH DTO Binding](012-direct-typed-patch-dto-binding.md)
- [ADR-013: Recursive Structured Value PATCH Semantics](013-recursive-structured-value-patch-semantics.md)
- [ADR-014: Flat Whole-Object Mapping for Resource-Side Meta](014-flat-whole-object-meta-mapping.md)
- [ADR-015: Mapper-Instance Construction for Jackson Adapters](015-jackson-adapter-construction.md)
- [ADR-016: Opt-in RelationshipLinkage for Resource Identifier Meta](016-resource-identifier-meta-mapping.md)
- [ADR-017: Ordinary Domain Relationships Remain Linkage-Oriented](017-relationship-data-presence-in-domain-mapping.md)
- [ADR-018: Major-Neutral Level-1 Application API Contract](018-level-one-application-api-contract.md)
- [ADR-019: Jackson-Neutral Implementation Helpers](019-jackson-neutral-implementation-helpers.md)
- [ADR-020: KazForge Namespace and Maven Group](020-kazforge-namespace.md)
- [ADR-021: Unified Release Train and Version Semantics](021-unified-release-train.md)
