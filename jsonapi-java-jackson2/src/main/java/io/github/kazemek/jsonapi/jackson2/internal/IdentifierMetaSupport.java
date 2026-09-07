package io.github.kazemek.jsonapi.jackson2.internal;

import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shared helpers for identifier-meta diagnostic locations and {@link ResourceIdentifier} copies
 * that preserve identifier meta (ADR-017).
 */
final class IdentifierMetaSupport {

  private IdentifierMetaSupport() {}

  /**
   * Resource-relative diagnostic location for to-one identifier meta, or for declaration failures
   * that address identifier meta as a whole.
   */
  static MappingLocation identifierMetaLocation(String relationshipName) {
    return MappingLocation.of(
        JsonApiMembers.RELATIONSHIPS, relationshipName, JsonApiMembers.DATA, JsonApiMembers.META);
  }

  /**
   * Resource-relative diagnostic location for one to-many linkage identifier's {@code meta} member.
   */
  static MappingLocation identifierMetaLocation(String relationshipName, int index) {
    return MappingLocation.of(
        JsonApiMembers.RELATIONSHIPS,
        relationshipName,
        JsonApiMembers.DATA,
        Integer.toString(index),
        JsonApiMembers.META);
  }

  /**
   * Copies linkage identity and identifier meta, dropping additional members (existing built-in
   * read-side conversion scope).
   */
  static ResourceIdentifier copyLinkageIdentifier(ResourceIdentifier identifier) {
    return new ResourceIdentifier(
        identifier.type(), identifier.id(), identifier.lid(), identifier.meta(), Map.of());
  }

  /** Overlays identifier meta while preserving type, id, lid, and additional members. */
  static ResourceIdentifier withMeta(ResourceIdentifier identifier, @Nullable Meta meta) {
    return copyPreservingAdditionalMembers(identifier, meta);
  }

  /**
   * NullAway models the generated {@link ResourceIdentifier} constructor as {@code Map<String,
   * Object>} additional members; the record component allows nullable values. The map is already
   * validated by the source identifier.
   */
  @SuppressWarnings("NullAway")
  private static ResourceIdentifier copyPreservingAdditionalMembers(
      ResourceIdentifier identifier, @Nullable Meta meta) {
    return new ResourceIdentifier(
        identifier.type(), identifier.id(), identifier.lid(), meta, identifier.additionalMembers());
  }
}
