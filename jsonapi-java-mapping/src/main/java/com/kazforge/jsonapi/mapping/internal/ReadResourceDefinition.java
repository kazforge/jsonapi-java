package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral view of one adapter-resolved read mapping: the JSON:API resource type plus the
 * mapped identity and ordered attribute/relationship properties.
 *
 * <p>Only the basic read surface is carried here. Whole-object meta and relationship-meta binding
 * stay adapter-owned in this extraction slice, so those properties do not appear in the neutral
 * view. Identity roles stay separate and independent: {@code identifier} maps only the {@code id}
 * member and {@code localId} only the {@code lid} member. Attribute and relationship properties
 * keep their mapping order, which is part of the read phase contract. Lists are defensively copied
 * so a definition is an immutable snapshot for the duration of one read. This is unsupported
 * implementation detail for backend cooperation, not consumer SPI.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record ReadResourceDefinition<P>(
    String resourceType,
    @Nullable ReadProperty<P> identifier,
    @Nullable ReadProperty<P> localId,
    List<ReadProperty<P>> attributes,
    List<ReadProperty<P>> relationships) {

  public ReadResourceDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(relationships, "relationships");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
  }
}
