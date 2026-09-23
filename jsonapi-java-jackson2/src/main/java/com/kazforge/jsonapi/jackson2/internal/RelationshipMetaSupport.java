package com.kazforge.jsonapi.jackson2.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.diagnostic.MappingLocation;

/** Shared helpers for resource-relative diagnostic locations. */
final class RelationshipMetaSupport {

  private RelationshipMetaSupport() {}

  /** Resource-relative diagnostic location for a relationship's linkage member. */
  static MappingLocation relationshipLocation(MappingPropertyView property) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), "data");
  }

  /** Resource-relative diagnostic location for the resource-side {@code meta} member. */
  static MappingLocation resourceMetaLocation() {
    return MappingLocation.of(JsonApiMembers.META);
  }

  /**
   * Resource-relative diagnostic location for a specific relationship's {@code meta} member, keyed
   * by the relationship's resolved JSON:API member name.
   */
  static MappingLocation relationshipMetaLocation(String relationshipName) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, relationshipName, JsonApiMembers.META);
  }
}
