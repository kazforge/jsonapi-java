package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;

/**
 * Minimal neutral view of one adapter-local relationship property consumed by shared linkage
 * binding.
 *
 * <p>{@link RelationshipLinkageBinder} only needs the property's logical identity for diagnostics
 * and its JSON:API member name for wire-relative locations; the backend keeps its own native
 * property token. Read and PATCH properties expose these through their own records, so this view
 * keeps the shared binder free of either direction's concrete property type.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 */
@NullMarked
public interface RelationshipBindingProperty {

  /** Backend-internal property identity used in diagnostics. */
  String logicalName();

  /** JSON:API member name on the wire. */
  String jsonapiName();
}
