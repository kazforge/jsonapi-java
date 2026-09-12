package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson.internal.patch.PresenceMarker;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.jackson.patch.PatchCommand;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import com.kazforge.jsonapi.jackson2.RelationshipLinkageMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Shared conversion support for the low-level {@link PatchCommand} path and the direct typed PATCH
 * DTO's identity and relationship-linkage paths.
 *
 * <p>Low-level attribute and whole-meta conversion runs against the property's explicit {@link
 * JavaType} target. Typed DTO atomic attributes and meta deliberately bypass this collaborator:
 * their JSON-compatible values remain in an internal {@link PresenceMarker}, and the contextual
 * {@link PatchPresence} deserializer performs the sole inner-type conversion during whole-DTO
 * construction.
 *
 * <p>Relationship linkage conversion uses the unwrapped {@link PatchPresence} type argument on the
 * DTO path. To-many detection, property-level {@code @JsonDeserialize} handling, primitive-null
 * rules, and linkage resolution use the applicable explicit target type. The low-level path also
 * performs final collection coercion here; the direct typed DTO path leaves final target coercion
 * to the contextual {@link PatchPresence} deserializer so the declared inner type is read once.
 *
 * <p>Diagnostic locations follow the {@link MappingLocation} contract: callers supply the failing
 * member's resource-relative location so failures never report Jackson logical property names.
 */
final class PatchMemberConverter {

  private static final MappingLocation IDENTIFIER_LOCATION = MappingLocation.of("id");

  private final JsonMapper mapper;
  private final PropertyScopedValueConverter propertyScoped;
  private final IdentifierConverter identifierConverter;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;

  PatchMemberConverter(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.propertyScoped = new PropertyScopedValueConverter(mapper);
    this.identifierConverter = Objects.requireNonNull(identifierConverter, "identifierConverter");
    this.linkageMappers = Map.copyOf(Objects.requireNonNull(linkageMappers, "linkageMappers"));
  }

  /** Converts a wire identifier into the identifier property's converted value (never null). */
  Object convertIdentity(
      String wireIdentifier,
      MappingProperty identifierProperty,
      JavaType beanType,
      Class<?> rawType) {
    Object parsed = parseIdentity(wireIdentifier, rawType);
    try {
      JavaType identifierType = identifierProperty.definition().getPrimaryType();
      Object converted =
          propertyScoped.convert(
              beanType,
              identifierProperty.definition().getFullName().getSimpleName(),
              identifierType,
              identifierType,
              parsed);
      return Objects.requireNonNull(converted, "identity");
    } catch (RuntimeException e) {
      throw identifierConversionFailed(rawType, e);
    }
  }

  /** Parses a JSON:API wire identifier without applying the target property's conversion yet. */
  Object parseIdentity(String wireIdentifier, Class<?> rawType) {
    Object parsed;
    try {
      parsed = identifierConverter.parse(wireIdentifier);
    } catch (RuntimeException e) {
      throw identifierConversionFailed(rawType, e);
    }
    if (parsed == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          IDENTIFIER_LOCATION,
          "Identifier converter returned null for the wire identifier at '"
              + IDENTIFIER_LOCATION
              + "'");
    }
    return parsed;
  }

  private static JsonApiMappingException identifierConversionFailed(
      Class<?> rawType, Throwable cause) {
    return new JsonApiMappingException(
        MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
        rawType,
        IDENTIFIER_LOCATION,
        "Failed to convert the wire identifier at '"
            + IDENTIFIER_LOCATION
            + "' for "
            + rawType.getName(),
        cause);
  }

  /**
   * Converts one supplied attribute value against {@code targetType}. Explicit JSON {@code null}
   * becomes raw Java {@code null} (low-level {@code PatchCommand} contract). Explicit null for a
   * primitive target is always {@link MappingDiagnostic#UNSUPPORTED_ATTRIBUTE_VALUE}, independent
   * of the caller's {@code FAIL_ON_NULL_FOR_PRIMITIVES} setting. Failures report the caller's
   * resource-relative attribute location (for example {@code /attributes/count}).
   */
  @Nullable Object convertAttribute(
      MappingProperty property,
      @Nullable Object rawValue,
      JavaType targetType,
      Class<?> rawType,
      JavaType beanType,
      MappingLocation attributeLocation) {
    if (rawValue == null && targetType.isPrimitive()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          rawType,
          attributeLocation,
          "Explicit null is not supported for primitive attribute '"
              + property.logicalName()
              + "' on "
              + rawType.getName());
    }
    try {
      if (rawValue == null) {
        return null;
      }
      return convertAttributeViaJackson(property, rawValue, targetType, beanType);
    } catch (RuntimeException e) {
      if (e instanceof JsonApiMappingException mappingException) {
        throw mappingException;
      }
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          rawType,
          attributeLocation,
          "Failed to convert attribute '" + property.logicalName() + "' for " + rawType.getName(),
          e);
    }
  }

  /**
   * Uses {@code convertValue} unless the property carries property-scoped Jackson deserialization
   * customization, in which case the shared {@link PropertyScopedValueConverter} runs the
   * property's fully-contextualized deserializer with a real context (same machinery as {@code
   * convertValue}, property-scoped). A null {@code rawValue} converts through the target type's
   * null value (for example {@code Optional.empty()} for an {@link Optional} target).
   */
  private @Nullable Object convertAttributeViaJackson(
      MappingProperty property, @Nullable Object rawValue, JavaType targetType, JavaType beanType) {
    return propertyScoped.convert(
        beanType,
        property.definition().getFullName().getSimpleName(),
        property.accessor().getType(),
        targetType,
        rawValue);
  }

  /**
   * Converts one whole-meta value atomically on the low-level path (ADR-015). Reuses the same
   * property-scoped Jackson authority as attribute conversion, but the caller supplies the
   * location-specific meta location so failures never surface an attribute-oriented pointer.
   */
  @Nullable Object convertWholeMeta(
      MappingProperty property,
      @Nullable Object rawValue,
      JavaType declaredType,
      JavaType beanType,
      MappingLocation metaLocation,
      Class<?> rawType) {
    try {
      return convertAttributeViaJackson(property, rawValue, declaredType, beanType);
    } catch (RuntimeException e) {
      if (e instanceof JsonApiMappingException mappingException) {
        throw mappingException;
      }
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          rawType,
          metaLocation,
          "Failed to convert the meta value at '" + metaLocation + "'",
          e);
    }
  }

  /**
   * Converts one relationship linkage against {@code targetType} (the unwrapped inner type on the
   * DTO path).
   */
  @Nullable Object convertRelationship(
      MappingProperty property, RelationshipData data, JavaType targetType) {
    return finalizeRelationshipValue(
        convertRelationshipLinkage(property, data, targetType), targetType, property);
  }

  /** Converts linkage for a typed PATCH DTO without coercing the complete inner target type yet. */
  @Nullable Object convertRelationshipForPatchDto(
      MappingProperty property, RelationshipData data, JavaType targetType) {
    return convertRelationshipLinkage(property, data, targetType);
  }

  private @Nullable Object convertRelationshipLinkage(
      MappingProperty property, RelationshipData data, JavaType targetType) {
    JavaType mappingType =
        RelationshipLinkageSupport.targetMappingType(targetType, mapper.getTypeFactory());
    RelationshipLinkageMapper linkageMapper =
        RelationshipLinkageSupport.selectLinkageMapper(targetType, property, linkageMappers);
    return RelationshipLinkageSupport.convertLinkage(
        property, data, linkageMapper, mappingType, mapper);
  }

  private @Nullable Object finalizeRelationshipValue(
      @Nullable Object intermediate, JavaType targetType, MappingProperty property) {
    if (alreadyConverted(intermediate, targetType)) {
      return intermediate;
    }
    try {
      return mapper.convertValue(intermediate, targetType);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET,
          RelationshipLinkageSupport.rawTypeOf(property),
          RelationshipLinkageSupport.relationshipLocation(property),
          "Failed to convert relationship '"
              + property.logicalName()
              + "' to "
              + targetType.toCanonical(),
          e);
    }
  }

  /**
   * True when {@code intermediate} is already a usable property value (for example a linkage-mapper
   * result) and does not need {@code convertValue} coercion to List/Set/array/Optional shapes.
   */
  private static boolean alreadyConverted(@Nullable Object intermediate, JavaType targetType) {
    if (intermediate == null || targetType.isTypeOrSubTypeOf(Optional.class)) {
      return false;
    }
    if (DomainResourceWriter.isToManyType(targetType)) {
      if (!(intermediate instanceof List<?> list)) {
        return targetType.getRawClass().isInstance(intermediate)
            && !needsCollectionCoercion(intermediate, targetType);
      }
      if (needsCollectionCoercion(intermediate, targetType)) {
        return false;
      }
      if (list.isEmpty()) {
        return targetType.getRawClass().isInstance(intermediate);
      }
      JavaType contentType = DomainResourceWriter.resolveContentType(targetType);
      if (contentType == null) {
        return false;
      }
      Object first = list.getFirst();
      return contentType.getRawClass().isInstance(first);
    }
    return targetType.getRawClass().isInstance(intermediate);
  }

  private static boolean needsCollectionCoercion(Object intermediate, JavaType targetType) {
    if (!DomainResourceWriter.isToManyType(targetType)) {
      return false;
    }
    if (targetType.isArrayType()) {
      return true;
    }
    return targetType.isTypeOrSubTypeOf(Set.class) && !(intermediate instanceof Set);
  }

  static Map<String, MappingProperty> byJsonapiName(List<MappingProperty> properties) {
    Map<String, MappingProperty> byName = new LinkedHashMap<>();
    for (MappingProperty property : properties) {
      byName.put(property.jsonapiName(), property);
    }
    return byName;
  }

  /** Unwraps a single exact {@code PatchPresence<T>} wrapper, leaving other types unchanged. */
  static JavaType unwrapPatchPresence(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1
        ? type.containedType(0)
        : type;
  }
}
