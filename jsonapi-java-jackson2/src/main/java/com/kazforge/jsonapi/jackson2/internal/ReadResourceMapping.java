package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.internal.ReadProperty;
import com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * JSON:API role and wire metadata for ordinary flat reads.
 *
 * <p>Unlike {@link ResourceMapping}, this mapping is resolved from Jackson's deserialization
 * introspection and records whether each mapped property has an effective deserialization target.
 * It intentionally does not replace or weaken the serialization-oriented write mapping.
 *
 * <p>{@code creatorPropertyNames} holds the configured Jackson external names of the target's
 * effective creator properties (deserialization-introspected properties carrying constructor
 * parameters). It feeds message-independent construction-failure classification: a missing-creator
 * input failure names one of these properties while that property is absent from the synthetic
 * input, whereas a supplied value's shape mismatch names a property that was supplied.
 */
record ReadResourceMapping(
    String resourceType,
    @Nullable ReadMappingProperty identifierProperty,
    @Nullable ReadMappingProperty localIdProperty,
    List<ReadMappingProperty> attributes,
    List<ReadMappingProperty> relationships,
    @Nullable ReadMappingProperty resourceMeta,
    List<ReadMappingProperty> relationshipMetaProperties,
    JavaType domainType,
    Set<String> creatorPropertyNames) {

  /**
   * Maps configured Jackson external names to resource-relative wire locations for
   * construction-failure translation. A supplied id role starts at {@code /id} and a supplied
   * local-id role at {@code /lid}; a null location leaves that role out of the map. The declared
   * type remains available for serialization-only properties so a missing member never creates a
   * synthetic input value merely by being present in the mapping.
   */
  Map<String, MappingConstructionStart> constructionStartsByJacksonName(
      @Nullable MappingLocation idLocation, @Nullable MappingLocation lidLocation) {
    Map<String, MappingConstructionStart> starts = new LinkedHashMap<>();
    if (identifierProperty != null && idLocation != null) {
      starts.put(
          identifierProperty.externalName(),
          new MappingConstructionStart(idLocation, identifierProperty.type()));
    }
    if (localIdProperty != null && lidLocation != null) {
      starts.put(
          localIdProperty.externalName(),
          new MappingConstructionStart(lidLocation, localIdProperty.type()));
    }
    for (ReadMappingProperty property : attributes) {
      starts.put(
          property.externalName(),
          new MappingConstructionStart(
              MappingLocation.of("attributes", property.jsonapiName()), property.type()));
    }
    for (ReadMappingProperty property : relationships) {
      starts.put(
          property.externalName(),
          new MappingConstructionStart(
              RelationshipMetaSupport.relationshipLocation(property), property.type()));
    }
    if (resourceMeta != null) {
      starts.put(
          resourceMeta.externalName(),
          new MappingConstructionStart(
              RelationshipMetaSupport.resourceMetaLocation(), resourceMeta.type()));
    }
    for (ReadMappingProperty property : relationshipMetaProperties) {
      starts.put(
          property.externalName(),
          new MappingConstructionStart(
              RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()),
              property.type()));
    }
    return starts;
  }

  /**
   * Builds the neutral read definition consumed by the shared basic reader: resource type, separate
   * identity roles, and ordered attribute/relationship properties, each carrying its semantic
   * metadata and effective-bindability state. Whole-meta and relationship-meta properties stay in
   * this adapter mapping because their binding remains adapter-owned.
   */
  ReadResourceDefinition<ReadMappingProperty> readDefinition() {
    List<ReadProperty<ReadMappingProperty>> attributeProperties =
        new ArrayList<>(attributes.size());
    for (ReadMappingProperty property : attributes) {
      attributeProperties.add(readProperty(property));
    }
    List<ReadProperty<ReadMappingProperty>> relationshipProperties =
        new ArrayList<>(relationships.size());
    for (ReadMappingProperty property : relationships) {
      relationshipProperties.add(readProperty(property));
    }
    return new ReadResourceDefinition<>(
        resourceType,
        readPropertyOrNull(identifierProperty),
        readPropertyOrNull(localIdProperty),
        attributeProperties,
        relationshipProperties);
  }

  private static ReadProperty<ReadMappingProperty> readProperty(ReadMappingProperty property) {
    return new ReadProperty<>(property, property.metadata(), property.deserializable());
  }

  private static @Nullable ReadProperty<ReadMappingProperty> readPropertyOrNull(
      @Nullable ReadMappingProperty property) {
    return property == null ? null : readProperty(property);
  }
}
