/**
 * Internal Jackson 2 write-side domain mapping engine, flat resource-to-DTO binding engine,
 * presence-aware PATCH binding engines, and module registration. Not a public API surface.
 *
 * <p>Mapping metadata, resource writing/binding, shared conversion/construction support, inclusion
 * traversal, PATCH binders/converters, and their mapper modules stay together here: they share
 * package-private records and helpers, and structured binding also serves flat-read failure-path
 * translation, so a mapping/PATCH split would broaden the internal surface without removing a
 * class-level cycle. The self-contained document codec lives in the sibling {@code codec}
 * subpackage; public facade/capability composition depends on both siblings, while the siblings
 * never depend on each other. Adapter-local {@code JavaType} shape tests, JSON:API-name property
 * indexing, diagnostic locations, and construction-path starts are owned by mapping metadata so
 * relationship and conversion support cannot call back into the writer. Relationship linkage
 * mapping contracts live in the public {@code mapping} subpackage.
 *
 * <p>The mapping engine resolves {@code ResourceMapping} definitions through configured Jackson
 * introspection (mapper-local cache keyed by complete {@code JavaType}), renders local resources
 * through property-scoped raw-value writers, traverses opt-in compound inclusion with
 * invocation-local state, and applies additive decoration. The binding engine resolves a separate
 * deserialization-oriented {@code ReadResourceMapping} view, assembles one synthetic property map
 * per resource, and constructs the target bean through a single {@code convertValue} so creators,
 * deserializers, null providers, and configured modules remain authoritative. Both PATCH paths
 * share the recursive structured-value engine and configured Jackson authority, but member
 * conversion is path-specific: low-level attributes and whole-meta values use {@code
 * PatchMemberConverter}; direct typed DTO atomic values remain JSON-compatible marker values until
 * the contextual {@code PatchPresence} deserializer performs the sole inner-type conversion. The
 * typed DTO path uses the shared converter for identity parsing and relationship linkage. See the
 * module README and ADR-005, ADR-012 through ADR-015, ADR-017, and ADR-018 for the mapping
 * contracts.
 */
@NullMarked
package com.kazforge.jsonapi.jackson2.internal;

import org.jspecify.annotations.NullMarked;
