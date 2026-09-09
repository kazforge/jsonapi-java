# Architecture Decision Records

ADRs record consequential, hard-to-reverse “why” decisions. The current cross-module mental model
lives in [`docs/architecture.md`](../architecture.md). Stable product direction lives in
[`docs/vision.md`](../vision.md); current capability lives in module READMEs and
[`docs/conformance.md`](../conformance.md). Owner and conflict rules are in
[`AGENTS.md`](../../AGENTS.md).

1. [ADR-001: Document Codec as the Product Boundary](001-product-boundary.md)
2. [ADR-002: Preserve JSON:API Wire States](002-document-representation.md)
3. [ADR-003: Strict Construction and Aggregate Validation](003-validation-and-immutability.md)
4. [ADR-004: Jackson Introspection Is Authoritative](004-jackson-integration.md)
5. [ADR-005: Separate Linkage from Inclusion](005-domain-mapping-and-inclusion.md)
6. [ADR-006: Document-First Deserialization](006-read-boundary.md)
7. [ADR-007: Optional Adapter Modules](007-module-boundaries.md)
8. [ADR-008: Public Namespace and Maven Group](008-public-namespace.md)
9. [ADR-009: JSpecify Nullness](009-jspecify-nullness.md)
10. [ADR-010: Architectural Tests for Module Boundaries](010-architectural-tests.md)
11. [ADR-011: Flat DTO Reads Remain Document-First](011-flat-dto-read-binding.md)
12. [ADR-012: Resource PATCH Produces Presence-Aware Commands](012-resource-patch-binding.md)
13. [ADR-013: Direct Typed PATCH DTO Binding](013-direct-typed-patch-dto-binding.md)
14. [ADR-014: Recursive Structured Value PATCH Semantics](014-recursive-structured-value-patch-semantics.md)
15. [ADR-015: Flat Whole-Object Mapping for Resource-Side Meta](015-flat-whole-object-meta-mapping.md)
16. [ADR-016: Mapper-Instance Construction for Jackson Adapters](016-jackson-adapter-construction.md)
17. [ADR-017: Opt-in RelationshipLinkage for Resource Identifier Meta](017-resource-identifier-meta-mapping.md)
18. [ADR-018: Ordinary Domain Relationships Remain Linkage-Oriented](018-relationship-data-presence-in-domain-mapping.md)
19. [ADR-019: Major-Neutral Level-1 Application API Contract](019-level-one-application-api-contract.md)
20. [ADR-020: Jackson-Neutral Implementation Helpers](020-jackson-neutral-implementation-helpers.md)
