package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral view of one adapter-resolved typed PATCH DTO mapping: the JSON:API resource type
 * plus the mapped {@code id} identity, ordered attribute and relationship properties, optional
 * resource meta, and matched relationship-meta properties.
 *
 * <p>It is projected from the adapter's serialization-oriented typed DTO mapping (the same member
 * set, names, ordering, declared types, and declaration/visibility decisions the typed binder
 * already used), deliberately separate from the deserialization-oriented {@link
 * PatchResourceDefinition} and {@link ReadResourceDefinition} projections. Attribute and
 * relationship properties keep their mapping order; each relationship-meta property carries the
 * matched target relationship's JSON:API name in its semantic metadata. Lists are defensively
 * copied so a definition is an immutable snapshot.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <P> opaque backend-native property token
 * @param <T> opaque backend-native type token
 */
@NullMarked
public record TypedPatchDefinition<P, T>(
    String resourceType,
    @Nullable TypedPatchProperty<P, T> identifier,
    List<TypedPatchProperty<P, T>> attributes,
    List<TypedPatchProperty<P, T>> relationships,
    @Nullable TypedPatchProperty<P, T> resourceMeta,
    List<TypedPatchProperty<P, T>> relationshipMetaProperties) {

  public TypedPatchDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(relationships, "relationships");
    Objects.requireNonNull(relationshipMetaProperties, "relationshipMetaProperties");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
    relationshipMetaProperties = List.copyOf(relationshipMetaProperties);
  }
}
