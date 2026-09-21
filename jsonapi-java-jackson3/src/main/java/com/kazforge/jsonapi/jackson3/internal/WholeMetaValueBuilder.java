package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.validation.JsonApiValidationException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

/**
 * Adapter-local conversion of one whole-meta property value into a core {@link Meta}.
 *
 * <p>Shared by the resource-meta phase, relationship-meta enrichment, and identifier-meta overlay
 * so the property-scoped conversion, object-shape rule, and stable {@link
 * MappingDiagnostic#INVALID_META_TARGET} family cannot drift between those locations. The converted
 * result must be a {@link Map}; scalar, array, or otherwise non-object values fail with the
 * location specific diagnostic instead of leaking a cast or core-validation failure.
 */
final class WholeMetaValueBuilder {

  private final PropertyScopedValueConverter propertyScoped;

  WholeMetaValueBuilder(PropertyScopedValueConverter propertyScoped) {
    this.propertyScoped = propertyScoped;
  }

  /**
   * Reads and converts one whole-meta property value. A null or backend-omitted value supplies no
   * meta. Failures report the location-specific {@code metaLocation}.
   */
  @Nullable Meta build(
      Object resource,
      JavaType domainType,
      MappingProperty property,
      MappingLocation metaLocation) {
    Object rawValue = MappingPropertyAccess.readValue(resource, property, property.role());
    Object value = MappingPropertyAccess.unwrapOptional(rawValue);
    if (value == null) {
      return null;
    }
    Object converted;
    try {
      PropertyScopedValueConverter.SerializationResult serialized =
          propertyScoped.serialize(
              domainType,
              property.definition().getFullName().getSimpleName(),
              resource,
              rawValue,
              value);
      if (!serialized.emitted()) {
        return null;
      }
      converted = serialized.value();
    } catch (RuntimeException e) {
      throw failure(resource, metaLocation, "Failed to convert meta value", e);
    }
    return wholeMetaValue(converted, resource, metaLocation, "meta");
  }

  /**
   * Builds identifier meta from an already-converted untyped value. A null converted value supplies
   * no identifier meta, matching the opt-in wrapper's existing semantics; the identifier-specific
   * diagnostic wording stays at the caller's location.
   */
  @Nullable Meta fromConvertedIdentifierMeta(
      @Nullable Object converted, Object resource, MappingLocation metaLocation) {
    return converted == null
        ? null
        : wholeMetaValue(converted, resource, metaLocation, "identifier meta");
  }

  /**
   * Converts an emitted value into a core {@link Meta}. Resource and relationship meta treat a
   * JSON-null emission as a non-object target failure rather than silently omitting the member;
   * identifier meta routes around this method for its null-supplies-nothing rule.
   */
  private static Meta wholeMetaValue(
      @Nullable Object converted, Object resource, MappingLocation metaLocation, String label) {
    if (!(converted instanceof Map<?, ?> map)) {
      throw failure(
          resource,
          metaLocation,
          "Converted "
              + label
              + " value is not an object (expected a JSON object, got "
              + convertedTypeName(converted)
              + ")",
          null);
    }
    try {
      return Meta.of(castMembers(map, resource, metaLocation));
    } catch (JsonApiValidationException e) {
      throw failure(resource, metaLocation, "Invalid " + label + " members", e);
    }
  }

  private static String convertedTypeName(@Nullable Object converted) {
    return converted == null ? "null" : converted.getClass().getName();
  }

  /**
   * Rebuilds the converted meta value into a string-keyed member map. The conversion target {@code
   * Object.class} always yields string keys (Jackson's untyped map representation), so the
   * non-string branch is defensive: it keeps the stable {@link
   * MappingDiagnostic#INVALID_META_TARGET} diagnostic at the known {@code metaLocation} instead of
   * leaking a class cast or a core-validation failure when conversion produces non-string keys.
   */
  private static Map<String, Object> castMembers(
      Map<?, ?> map, Object resource, MappingLocation metaLocation) {
    Map<String, Object> members = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String stringKey)) {
        throw new JsonApiMappingException(
            MappingDiagnostic.INVALID_META_TARGET,
            resource.getClass(),
            metaLocation,
            "Meta object key is not a string: " + key);
      }
      members.put(stringKey, entry.getValue());
    }
    return members;
  }

  private static JsonApiMappingException failure(
      Object resource, MappingLocation metaLocation, String message, @Nullable Throwable cause) {
    return cause == null
        ? new JsonApiMappingException(
            MappingDiagnostic.INVALID_META_TARGET, resource.getClass(), metaLocation, message)
        : new JsonApiMappingException(
            MappingDiagnostic.INVALID_META_TARGET,
            resource.getClass(),
            metaLocation,
            message,
            cause);
  }
}
