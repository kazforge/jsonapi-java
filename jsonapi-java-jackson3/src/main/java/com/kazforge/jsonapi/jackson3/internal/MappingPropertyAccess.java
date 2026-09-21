package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Adapter-local property access and shared member-location rules for mapped Jackson writes.
 *
 * <p>Read failures keep the role's diagnostic family and resource-relative wire location; the
 * JSON:API member name is escaped as pointer segments, never the Jackson logical name. Relationship
 * collection normalization preserves the unsupported-shape diagnostic with the failing
 * relationship's location.
 */
final class MappingPropertyAccess {

  private MappingPropertyAccess() {}

  static @Nullable Object unwrapOptional(@Nullable Object value) {
    if (value instanceof Optional<?> optional) {
      return optional.orElse(null);
    }
    return value;
  }

  /**
   * Converts an already-read to-many value into a list of elements. Serialization callers supply
   * the failing relationship's resource-relative location; callers without a JSON:API member
   * coordinate pass null.
   */
  static List<Object> convertToCollection(
      Object value, @Nullable MappingLocation relationshipLocation) {
    return switch (value) {
      case List<?> list -> {
        List<Object> result = new ArrayList<>(list.size());
        result.addAll(list);
        yield result;
      }
      case Object[] array -> {
        List<Object> result = new ArrayList<>(array.length);
        Collections.addAll(result, array);
        yield result;
      }
      case Iterable<?> iterable -> {
        List<Object> result = new ArrayList<>();
        for (Object item : iterable) {
          result.add(item);
        }
        yield result;
      }
      default -> throw relationshipShapeFailure(value, relationshipLocation);
    };
  }

  private static JsonApiMappingException relationshipShapeFailure(
      Object value, @Nullable MappingLocation relationshipLocation) {
    String message =
        "To-many relationship value is not a supported collection type: "
            + value.getClass().getName();
    return relationshipLocation == null
        ? JsonApiMappingException.withoutLocation(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE, value.getClass(), message)
        : new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
            value.getClass(),
            relationshipLocation,
            message);
  }

  static @Nullable Object readValue(Object resource, MappingProperty property, PropertyRole role) {
    try {
      return property.accessor().getValue(resource);
    } catch (JsonApiMappingException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonApiMappingException(
          diagnosticFor(role),
          resource.getClass(),
          memberLocation(property, role),
          "Failed to read property '"
              + property.logicalName()
              + "' ("
              + role.name().toLowerCase()
              + ")",
          e);
    }
  }

  /**
   * Resource-relative wire location of one mapped member, per the mapping-location contract: the
   * JSON:API member name is escaped as pointer segments, never the Jackson logical name.
   */
  static MappingLocation memberLocation(MappingProperty property, PropertyRole role) {
    return switch (role) {
      case ID -> MappingLocation.of("id");
      case LOCAL_ID -> MappingLocation.of("lid");
      case ATTRIBUTE -> MappingLocation.of("attributes", property.jsonapiName());
      case RELATIONSHIP -> MappingLocation.of("relationships", property.jsonapiName(), "data");
      case RESOURCE_META -> RelationshipMetaSupport.resourceMetaLocation();
      case RELATIONSHIP_META ->
          RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName());
    };
  }

  private static MappingDiagnostic diagnosticFor(PropertyRole role) {
    return switch (role) {
      case ID, LOCAL_ID -> MappingDiagnostic.MISSING_IDENTIFIER;
      case ATTRIBUTE -> MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE;
      case RELATIONSHIP -> MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE;
      case RESOURCE_META, RELATIONSHIP_META -> MappingDiagnostic.INVALID_META_TARGET;
    };
  }
}
