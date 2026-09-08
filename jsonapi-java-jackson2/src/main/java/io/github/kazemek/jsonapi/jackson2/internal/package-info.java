/**
 * Internal Jackson 2 serializer registration, streaming wire emission, token-driven document
 * decoding, the write-side domain mapping engine, the flat resource-to-DTO binding engine, and the
 * presence-aware PATCH binding engines. Not a public API surface.
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
package io.github.kazemek.jsonapi.jackson2.internal;

import org.jspecify.annotations.NullMarked;
