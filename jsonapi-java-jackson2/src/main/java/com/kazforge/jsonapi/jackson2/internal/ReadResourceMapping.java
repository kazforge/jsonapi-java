package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
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
  Map<String, FlatConstructionPaths.ConstructionStart> constructionStartsByJacksonName(
      @Nullable MappingLocation idLocation, @Nullable MappingLocation lidLocation) {
    Map<String, FlatConstructionPaths.ConstructionStart> starts = new LinkedHashMap<>();
    if (identifierProperty != null && idLocation != null) {
      starts.put(
          identifierProperty.jacksonName(),
          new FlatConstructionPaths.ConstructionStart(idLocation, identifierProperty.type()));
    }
    if (localIdProperty != null && lidLocation != null) {
      starts.put(
          localIdProperty.jacksonName(),
          new FlatConstructionPaths.ConstructionStart(lidLocation, localIdProperty.type()));
    }
    for (ReadMappingProperty property : attributes) {
      starts.put(
          property.jacksonName(),
          new FlatConstructionPaths.ConstructionStart(
              MappingLocation.of("attributes", property.jsonapiName()), property.type()));
    }
    for (ReadMappingProperty property : relationships) {
      starts.put(
          property.jacksonName(),
          new FlatConstructionPaths.ConstructionStart(
              RelationshipLinkageSupport.relationshipLocation(property), property.type()));
    }
    if (resourceMeta != null) {
      starts.put(
          resourceMeta.jacksonName(),
          new FlatConstructionPaths.ConstructionStart(
              RelationshipMetaSupport.resourceMetaLocation(), resourceMeta.type()));
    }
    for (ReadMappingProperty property : relationshipMetaProperties) {
      starts.put(
          property.jacksonName(),
          new FlatConstructionPaths.ConstructionStart(
              RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()),
              property.type()));
    }
    return starts;
  }
}
