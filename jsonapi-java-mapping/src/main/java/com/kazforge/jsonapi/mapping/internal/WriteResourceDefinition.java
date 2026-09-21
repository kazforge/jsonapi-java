package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral view of one adapter-resolved write mapping: the JSON:API resource type plus the
 * mapped identity, attribute, and relationship properties in declaration order.
 *
 * <p>Whole-meta and decoration state stay adapter-owned and are intentionally absent: those phases
 * are applied around the shared basic write, not by it. Lists are defensively copied so a
 * definition is an immutable snapshot for the duration of one write. This is unsupported
 * implementation detail for backend cooperation, not consumer SPI.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record WriteResourceDefinition<P>(
    String resourceType,
    @Nullable WriteProperty<P> identifier,
    @Nullable WriteProperty<P> localId,
    List<WriteProperty<P>> attributes,
    List<WriteProperty<P>> relationships) {

  public WriteResourceDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(relationships, "relationships");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
  }
}
