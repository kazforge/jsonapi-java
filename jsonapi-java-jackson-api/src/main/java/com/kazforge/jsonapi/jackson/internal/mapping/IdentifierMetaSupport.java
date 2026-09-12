package com.kazforge.jsonapi.jackson.internal.mapping;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Shared helpers for identifier-meta locations and {@link ResourceIdentifier} copies. */
public final class IdentifierMetaSupport {

  private IdentifierMetaSupport() {}

  public static MappingLocation identifierMetaLocation(String relationshipName) {
    return MappingLocation.of(
        JsonApiMembers.RELATIONSHIPS, relationshipName, JsonApiMembers.DATA, JsonApiMembers.META);
  }

  public static MappingLocation identifierMetaLocation(String relationshipName, int index) {
    return MappingLocation.of(
        JsonApiMembers.RELATIONSHIPS,
        relationshipName,
        JsonApiMembers.DATA,
        Integer.toString(index),
        JsonApiMembers.META);
  }

  /** Copies linkage identity and identifier meta, dropping additional members. */
  public static ResourceIdentifier copyLinkageIdentifier(ResourceIdentifier identifier) {
    return new ResourceIdentifier(
        identifier.type(), identifier.id(), identifier.lid(), identifier.meta(), Map.of());
  }

  /** Overlays identifier meta while preserving type, id, lid, and additional members. */
  public static ResourceIdentifier withMeta(ResourceIdentifier identifier, @Nullable Meta meta) {
    return copyPreservingAdditionalMembers(identifier, meta);
  }

  @SuppressWarnings("NullAway")
  private static ResourceIdentifier copyPreservingAdditionalMembers(
      ResourceIdentifier identifier, @Nullable Meta meta) {
    return new ResourceIdentifier(
        identifier.type(), identifier.id(), identifier.lid(), meta, identifier.additionalMembers());
  }
}
