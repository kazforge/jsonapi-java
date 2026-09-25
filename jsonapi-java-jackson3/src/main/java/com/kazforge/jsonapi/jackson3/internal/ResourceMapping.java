package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

record ResourceMapping(
    String resourceType,
    @Nullable MappingProperty identifierProperty,
    @Nullable MappingProperty localIdProperty,
    List<MappingProperty> attributes,
    List<MappingProperty> relationships,
    @Nullable MappingProperty resourceMeta,
    List<MappingProperty> relationshipMetaProperties,
    JavaType domainType) {

  /**
   * Maps every member's configured Jackson external name to its construction-translation start: the
   * member's resource-relative wire location plus declared type. A supplied id role starts at
   * {@code /id}, a supplied local-id role at {@code /lid}; attributes start at {@code
   * /attributes/<wire-name>}, relationships at {@code /relationships/<wire-name>/data}, resource
   * meta at {@code /meta}, and relationship meta at {@code /relationships/<wire-name>/meta}.
   * Consumed by the typed PATCH DTO binder so construction-failure translation uses the write
   * mapping's declared types. A null location leaves that identity role out of the map: an
   * unsupplied member never becomes a synthetic construction input.
   */
  Map<String, MappingConstructionStart> constructionStartsByJacksonName(
      @Nullable MappingLocation idLocation, @Nullable MappingLocation lidLocation) {
    Map<String, MappingConstructionStart> starts = new LinkedHashMap<>();
    if (identifierProperty != null && idLocation != null) {
      starts.put(
          identifierProperty.externalName(),
          new MappingConstructionStart(idLocation, identifierProperty.accessor().getType()));
    }
    if (localIdProperty != null && lidLocation != null) {
      starts.put(
          localIdProperty.externalName(),
          new MappingConstructionStart(lidLocation, localIdProperty.accessor().getType()));
    }
    for (MappingProperty property : attributes) {
      starts.put(
          property.externalName(),
          new MappingConstructionStart(
              MappingLocation.of("attributes", property.jsonapiName()),
              property.accessor().getType()));
    }
    for (MappingProperty property : relationships) {
      starts.put(
          property.externalName(),
          new MappingConstructionStart(
              RelationshipMetaSupport.relationshipLocation(property),
              property.accessor().getType()));
    }
    if (resourceMeta != null) {
      starts.put(
          resourceMeta.externalName(),
          new MappingConstructionStart(
              RelationshipMetaSupport.resourceMetaLocation(), resourceMeta.accessor().getType()));
    }
    for (MappingProperty property : relationshipMetaProperties) {
      starts.put(
          property.externalName(),
          new MappingConstructionStart(
              RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()),
              property.accessor().getType()));
    }
    return starts;
  }
}
