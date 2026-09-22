package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral view of one adapter-resolved read mapping: the JSON:API resource type plus the
 * mapped identity, ordered attribute/relationship, resource-meta, and matched relationship-meta
 * properties.
 *
 * <p>Identity roles stay separate and independent: {@code identifier} maps only the {@code id}
 * member and {@code localId} only the {@code lid} member. Attribute, relationship, and
 * relationship-meta properties keep their mapping order, which is part of the read phase contract.
 * {@code resourceMeta} is the single mapped whole-object {@code meta} property and may be absent.
 * Each relationship-meta property carries the matched target relationship's JSON:API name in its
 * semantic metadata, so its wire location is the referenced relationship's {@code meta} member.
 * Lists are defensively copied so a definition is an immutable snapshot for the duration of one
 * read. This is unsupported implementation detail for backend cooperation, not consumer SPI.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record ReadResourceDefinition<P>(
    String resourceType,
    @Nullable ReadProperty<P> identifier,
    @Nullable ReadProperty<P> localId,
    List<ReadProperty<P>> attributes,
    List<ReadProperty<P>> relationships,
    @Nullable ReadProperty<P> resourceMeta,
    List<ReadProperty<P>> relationshipMetaProperties) {

  public ReadResourceDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(attributes, "attributes");
    Objects.requireNonNull(relationships, "relationships");
    Objects.requireNonNull(relationshipMetaProperties, "relationshipMetaProperties");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
    relationshipMetaProperties = List.copyOf(relationshipMetaProperties);
  }
}
