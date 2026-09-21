package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral pairing of one adapter-native write property token with its {@link
 * SemanticProperty} metadata.
 *
 * <p>The token stays opaque to the shared mapping domain: a backend may use a Jackson property
 * record, an accessor handle, or another native representation. Only the semantic role and names
 * participate in shared write semantics. This is unsupported implementation detail for backend
 * cooperation, not consumer SPI, and must not appear in supported backend signatures.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record WriteProperty<P>(P token, SemanticProperty metadata) {

  public WriteProperty {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(metadata, "metadata");
  }

  public PropertyRole role() {
    return metadata.role();
  }

  public String logicalName() {
    return metadata.logicalName();
  }

  public String jsonapiName() {
    return metadata.jsonapiName();
  }
}
