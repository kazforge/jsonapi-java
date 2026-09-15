/**
 * Internal Jackson 3 domain-to-resource mapping engine (direction-specific definition resolvers and
 * caches, writer, binders, shared relationship linkage support, low-level presence-aware PATCH
 * member conversion, and direct typed PATCH DTO marker/deserializer support), and module
 * registration. Not a public API surface.
 *
 * <p>Mapping metadata, resource writing/binding, shared conversion/construction support, inclusion
 * traversal, PATCH binders/converters, and their mapper modules stay together here: they share
 * package-private records and helpers, and structured binding also serves flat-read failure-path
 * translation, so a mapping/PATCH split would broaden the internal surface without removing a
 * class-level cycle. The self-contained document codec lives in the sibling {@code codec}
 * subpackage; public facade/capability composition depends on both siblings, while the siblings
 * never depend on each other. Adapter-local {@code JavaType} shape tests, JSON:API-name property
 * indexing, diagnostic locations, and construction-path starts are owned by mapping metadata so
 * relationship and conversion support cannot call back into the writer. Ordinary flat reads
 * translate construction paths through the dedicated flat translator, while recursive PATCH binding
 * keeps the structured-value engine. Relationship linkage mapping contracts live in the public
 * {@code mapping} subpackage.
 *
 * <p>Typed DTO atomic values remain JSON-compatible until the contextual {@code PatchPresence}
 * deserializer performs the sole inner-type conversion; relationship linkage uses its dedicated
 * conversion path. The read binder selects supplied properties from Jackson's effective
 * deserialization model; the canonical resource mapping remains serialization-oriented for writes.
 */
@NullMarked
package com.kazforge.jsonapi.jackson3.internal;

import org.jspecify.annotations.NullMarked;
