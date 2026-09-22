package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.mapping.internal.ReadProperty;
import com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * JSON:API role and wire metadata for ordinary flat reads.
 *
 * <p>Unlike {@link ResourceMapping}, this mapping is resolved from Jackson's effective
 * deserialization model and records whether each mapped property has a bindable effective property.
 * It intentionally does not replace or weaken the serialization-oriented write mapping.
 *
 * <p>Creator participation is read from the effective properties rather than re-inferred from a
 * serialization description: the external names of the mapped effective creator properties feed
 * message-independent construction-failure classification.
 */
record ReadResourceMapping(
    String resourceType,
    @Nullable ReadMappingProperty identifierProperty,
    @Nullable ReadMappingProperty localIdProperty,
    List<ReadMappingProperty> attributes,
    List<ReadMappingProperty> relationships,
    @Nullable ReadMappingProperty resourceMeta,
    List<ReadMappingProperty> relationshipMetaProperties,
    JavaType domainType) {

  /**
   * The configured Jackson external names of the mapped effective creator properties. A
   * missing-creator input failure names one of these properties while that property is absent from
   * the synthetic input, whereas a supplied value's shape mismatch names a property that was
   * supplied.
   */
  Set<String> creatorPropertyNames() {
    Set<String> names = new HashSet<>();
    addCreatorName(names, identifierProperty);
    addCreatorName(names, localIdProperty);
    for (ReadMappingProperty property : attributes) {
      addCreatorName(names, property);
    }
    for (ReadMappingProperty property : relationships) {
      addCreatorName(names, property);
    }
    addCreatorName(names, resourceMeta);
    for (ReadMappingProperty property : relationshipMetaProperties) {
      addCreatorName(names, property);
    }
    return Set.copyOf(names);
  }

  private static void addCreatorName(Set<String> names, @Nullable ReadMappingProperty property) {
    if (property != null && property.creatorProperty()) {
      names.add(property.externalName());
    }
  }

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
