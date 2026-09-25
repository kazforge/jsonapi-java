package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral pairing of one adapter-native read property token with its {@link
 * SemanticProperty} metadata and its effective-bindability state.
 *
 * <p>The token stays opaque to the shared mapping domain: a backend may use a Jackson
 * deserialization-introspection record, an accessor handle, or another native representation. Only
 * the semantic role, names, and whether the configured backend has an effective deserialization
 * target participate in shared read semantics. {@code bindable} is {@code false} for a supplied
 * member whose serialization-only declaration has no effective deserialization target; the shared
 * reader then fails with the existing non-deserializable diagnostic at that member's wire location.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend signatures.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record ReadProperty<P>(P token, SemanticProperty metadata, boolean bindable)
    implements RelationshipBindingProperty {

  public ReadProperty {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(metadata, "metadata");
  }

  @Override
  public String logicalName() {
    return metadata.logicalName();
  }

  public String externalName() {
    return metadata.externalName();
  }

  @Override
  public String jsonapiName() {
    return metadata.jsonapiName();
  }
}
