package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.mapping.internal.ReadProperty;
import com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

/**
 * JSON:API role and wire metadata for ordinary flat reads.
 *
 * <p>Unlike {@link ResourceMapping}, this mapping is resolved from Jackson's effective
 * deserialization model and records whether each mapped property has a bindable effective property.
 * It intentionally does not replace or weaken the serialization-oriented write mapping.
 *
 * <p>Creator participation is read from the configured bean deserializer's effective creator
 * properties rather than re-inferred from a serialization description: the external names of every
 * non-injection, view-eligible effective creator property, including unannotated properties that
 * are absent from the JSON:API read mapping, feed message-independent construction-failure
 * classification.
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
   * Builds the neutral read definition consumed by the shared reader: resource type, separate
   * identity roles, and ordered attribute, relationship, resource-meta, and matched
   * relationship-meta properties, each carrying its semantic metadata and effective-bindability
   * state. Relationship meta properties keep the resolved target relationship's JSON:API name.
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
    List<ReadProperty<ReadMappingProperty>> relationshipMeta = new ArrayList<>();
    for (ReadMappingProperty property : relationshipMetaProperties) {
      relationshipMeta.add(readProperty(property));
    }
    return new ReadResourceDefinition<>(
        resourceType,
        readPropertyOrNull(identifierProperty),
        readPropertyOrNull(localIdProperty),
        attributeProperties,
        relationshipProperties,
        readPropertyOrNull(resourceMeta),
        relationshipMeta);
  }

  private static ReadProperty<ReadMappingProperty> readProperty(ReadMappingProperty property) {
    return new ReadProperty<>(property, property.metadata(), property.deserializable());
  }

  private static @Nullable ReadProperty<ReadMappingProperty> readPropertyOrNull(
      @Nullable ReadMappingProperty property) {
    return property == null ? null : readProperty(property);
  }
}
