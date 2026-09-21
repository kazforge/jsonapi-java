package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral accessor surface for adapter-local mapping-property views that compose a {@link
 * SemanticProperty}.
 *
 * <p>Each backend keeps its own write and read property records and native handles; this carrier
 * shares only the role and naming accessors over the composed metadata, so adapter views and their
 * callers cannot drift in how the logical, external, and JSON:API names are read.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 */
@NullMarked
public interface SemanticPropertyCarrier {

  SemanticProperty metadata();

  /**
   * Backend-internal property identity (for Jackson, the Java field, record component, or bean
   * name).
   */
  default String logicalName() {
    return metadata().logicalName();
  }

  /** Configured backend external name used as the construction and conversion map key. */
  default String externalName() {
    return metadata().externalName();
  }

  /** JSON:API member name on the wire. */
  default String jsonapiName() {
    return metadata().jsonapiName();
  }

  default PropertyRole role() {
    return metadata().role();
  }
}
