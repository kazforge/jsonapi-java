# Architecture Decision Records

ADRs record consequential, hard-to-reverse “why” decisions. The current cross-module mental model
lives in [`docs/architecture.md`](../architecture.md). Stable product direction lives in
[`docs/vision.md`](../vision.md); current capability lives in module READMEs and
[`docs/conformance.md`](../conformance.md). Owner and conflict rules are in
[`AGENTS.md`](../../AGENTS.md).

- [ADR-001: Document Codec as the Product Boundary](001-product-boundary.md)
- [ADR-002: Preserve JSON:API Wire States](002-document-representation.md)
- [ADR-003: Strict Construction and Aggregate Validation](003-validation-and-immutability.md)
- [ADR-004: Configured Jackson Is the Mapping Authority](004-jackson-integration.md)
- [ADR-005: Separate Linkage from Inclusion](005-domain-mapping-and-inclusion.md)
- [ADR-006: Document-First Deserialization](006-read-boundary.md)
- [ADR-007: Optional Adapter Modules](007-module-boundaries.md)
- [ADR-008: JSpecify Nullness](008-jspecify-nullness.md)
- [ADR-009: Architectural Tests for Module Boundaries](009-architectural-tests.md)
- [ADR-010: Presence-Aware Resource PATCH Binding](010-resource-patch-binding.md)
- [ADR-011: Distinct Whole-Object Meta Locations](011-flat-whole-object-meta-mapping.md)
- [ADR-012: Major-Neutral Level-1 Application API Contract](012-level-one-application-api-contract.md)
- [ADR-013: KazForge Namespace and Maven Group](013-kazforge-namespace.md)
- [ADR-014: Unified Release Train and Version Semantics](014-unified-release-train.md)
- [ADR-015: Share Mapping Semantics, Keep Native Wire Codecs](015-responsibility-based-mapping-and-native-wire-codecs.md)
