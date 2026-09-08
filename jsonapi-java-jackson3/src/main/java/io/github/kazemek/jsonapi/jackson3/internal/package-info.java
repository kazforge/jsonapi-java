/**
 * Internal Jackson 3 serializers, token-driven document decoding, module registration, and
 * domain-to-resource mapping engine (direction-specific definition resolvers and caches, writer,
 * binders, shared relationship linkage support, low-level presence-aware PATCH member conversion,
 * and direct typed PATCH DTO marker/deserializer support). Typed DTO atomic values remain
 * JSON-compatible until the contextual {@code PatchPresence} deserializer performs the sole
 * inner-type conversion; relationship linkage uses its dedicated conversion path. The read binder
 * selects supplied properties from Jackson's effective deserialization model; the canonical
 * resource mapping remains serialization-oriented for writes. Not a public API surface.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson3.internal;

import org.jspecify.annotations.NullMarked;
