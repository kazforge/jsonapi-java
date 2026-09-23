package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral view of one adapter-resolved inbound PATCH mapping: the JSON:API resource type
 * plus the mapped {@code id} identity, ordered attribute and relationship properties, optional
 * resource meta, and matched relationship-meta properties.
 *
 * <p>It is deliberately separate from {@link ReadResourceDefinition}: ordinary reads and low-level
 * PATCH are different projections of one configured inbound mapping. Low-level PATCH identity is
 * the {@code id} member only and never falls back to {@code lid}. Attribute and relationship
 * properties keep their mapping order; the shared binder selects changes in wire encounter order,
 * so this order is the lookup authority rather than the emitted change order. Each
 * relationship-meta property carries the matched target relationship's JSON:API name in its
 * semantic metadata, so its wire location is the referenced relationship's {@code meta} member.
 * Lists are defensively copied so a definition is an immutable snapshot.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record PatchResourceDefinition<P>(
    String resourceType,
    @Nullable PatchProperty<P> identifier,
    List<PatchProperty<P>> attributes,
    List<PatchProperty<P>> relationships,
    @Nullable PatchProperty<P> resourceMeta,
    List<PatchProperty<P>> relationshipMetaProperties) {

  public PatchResourceDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(relationships, "relationships");
    Objects.requireNonNull(relationshipMetaProperties, "relationshipMetaProperties");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
    relationshipMetaProperties = List.copyOf(relationshipMetaProperties);
  }
}
