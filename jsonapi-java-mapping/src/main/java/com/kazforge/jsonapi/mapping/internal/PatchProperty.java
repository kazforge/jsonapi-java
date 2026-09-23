package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral pairing of one adapter-native inbound PATCH property token with its {@link
 * SemanticProperty} metadata and its effective-bindability state.
 *
 * <p>The token stays opaque to the shared mapping domain: a backend may use a Jackson
 * deserialization-introspection record or another native representation. Only the semantic role,
 * names, and whether the configured backend has an effective deserialization target participate in
 * shared low-level PATCH semantics. {@code bindable} is {@code false} for a supplied member whose
 * declaration has no effective deserialization target; the shared binder then fails with the
 * non-deserializable diagnostic at that member's wire location instead of converting from a
 * serialization accessor.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend signatures.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record PatchProperty<P>(P token, SemanticProperty metadata, boolean bindable)
    implements RelationshipBindingProperty {

  public PatchProperty {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(metadata, "metadata");
  }

  public PropertyRole role() {
    return metadata.role();
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
