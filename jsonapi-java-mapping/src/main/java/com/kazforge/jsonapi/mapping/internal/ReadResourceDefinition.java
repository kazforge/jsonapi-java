package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Maps every bindable read property's backend external name to its resource-relative
   * construction-path start, in read mapping order. Identity starts are included only when the
   * corresponding wire member was supplied, signalled by a non-null location from the shared read
   * result; a supplied non-bindable member already fails in the shared reader before construction,
   * and an absent required creator has an effective native property and is therefore bindable.
   *
   * <p>The only neutral fact shared here is the backend-name to JSON:API {@link MappingLocation}
   * translation: supplied {@code /id} and {@code /lid}, {@code /attributes/<jsonApiName>}, {@code
   * /relationships/<jsonApiName>/data}, {@code /meta}, and {@code
   * /relationships/<targetJsonApiName>/meta}. Nested walking, effective native types, and
   * configured deserialization remain adapter-owned; the paired token gives the adapter its
   * effective property without a second lookup. This is unsupported implementation detail for
   * backend cooperation, not consumer SPI.
   */
  public Map<String, ReadConstructionStart<P>> constructionStarts(
      @Nullable MappingLocation identifierLocation, @Nullable MappingLocation localIdLocation) {
    Map<String, ReadConstructionStart<P>> starts = new LinkedHashMap<>();
    addStart(starts, identifier, identifierLocation);
    addStart(starts, localId, localIdLocation);
    for (ReadProperty<P> property : attributes) {
      addStart(
          starts, property, MappingLocation.of(JsonApiMembers.ATTRIBUTES, property.jsonapiName()));
    }
    for (ReadProperty<P> property : relationships) {
      addStart(
          starts,
          property,
          MappingLocation.of(
              JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA));
    }
    addStart(starts, resourceMeta, MappingLocation.of(JsonApiMembers.META));
    for (ReadProperty<P> property : relationshipMetaProperties) {
      addStart(
          starts,
          property,
          MappingLocation.of(
              JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.META));
    }
    return Collections.unmodifiableMap(starts);
  }

  private static <P> void addStart(
      Map<String, ReadConstructionStart<P>> starts,
      @Nullable ReadProperty<P> property,
      @Nullable MappingLocation location) {
    if (property == null || location == null || !property.bindable()) {
      return;
    }
    starts.put(property.externalName(), new ReadConstructionStart<>(location, property));
  }
}
