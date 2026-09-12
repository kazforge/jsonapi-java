package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import java.util.List;
import java.util.Map;

/** Shared helpers for whole-meta mapping properties and diagnostic locations (ADR-015). */
final class RelationshipMetaSupport {

  private RelationshipMetaSupport() {}

  /**
   * Builds a target-relationship-jsonapi-name to relationship-meta property map. The resolver
   * guarantees at most one relationship-meta property per target, so keys are unique.
   */
  static Map<String, MappingProperty> byTarget(List<MappingProperty> relationshipMetaProperties) {
    return PatchMemberConverter.byJsonapiName(relationshipMetaProperties);
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
