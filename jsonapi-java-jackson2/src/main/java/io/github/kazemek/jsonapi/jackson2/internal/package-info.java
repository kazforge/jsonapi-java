/**
 * Internal Jackson 2 serializer registration, streaming wire emission, token-driven document
 * decoding, and the write-side domain mapping engine. Not a public API surface.
 *
 * <p>The mapping engine resolves {@code ResourceMapping} definitions through configured Jackson
 * introspection (mapper-local cache keyed by complete {@code JavaType}), renders local resources
 * through property-scoped raw-value writers, traverses opt-in compound inclusion with
 * invocation-local state, and applies additive decoration. See the module README and ADR-005,
 * ADR-015, ADR-017, and ADR-018 for the mapping contracts.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson2.internal;

import org.jspecify.annotations.NullMarked;
