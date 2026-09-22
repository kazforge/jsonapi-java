package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral view of one adapter-resolved write mapping: the JSON:API resource type plus the
 * mapped identity, attribute, relationship, resource-meta, and relationship-meta properties in
 * declaration order.
 *
 * <p>Resource-meta and relationship-meta properties carry their semantic role and JSON:API target
 * names while their native tokens stay opaque. A relationship-meta property's {@link
 * WriteProperty#jsonapiName()} is the matched target relationship's JSON:API member name, so the
 * shared writer can attach each relationship meta to its selected relationship. Decoration and
 * configured conversion stay adapter-owned. Lists are defensively copied so a definition is an
 * immutable snapshot for the duration of one write. This is unsupported implementation detail for
 * backend cooperation, not consumer SPI.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record WriteResourceDefinition<P>(
    String resourceType,
    @Nullable WriteProperty<P> identifier,
    @Nullable WriteProperty<P> localId,
    List<WriteProperty<P>> attributes,
    List<WriteProperty<P>> relationships,
    @Nullable WriteProperty<P> resourceMeta,
    List<WriteProperty<P>> relationshipMeta) {

  public WriteResourceDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(relationships, "relationships");
    Objects.requireNonNull(relationshipMeta, "relationshipMeta");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
    relationshipMeta = List.copyOf(relationshipMeta);
  }

  /**
   * Returns the single relationship-meta property matched to {@code relationshipName}, or {@code
   * null} when the relationship has no mapped meta. The resolver guarantees at most one
   * relationship-meta property per target relationship.
   */
  public @Nullable WriteProperty<P> relationshipMetaFor(String relationshipName) {
    for (WriteProperty<P> property : relationshipMeta) {
      if (property.jsonapiName().equals(relationshipName)) {
        return property;
      }
    }
    return null;
  }
}
