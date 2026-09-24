package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.internal.PresenceMarker;
import com.kazforge.jsonapi.patch.PatchCommand;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

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
      ReadMappingProperty identifierProperty,
      JavaType beanType,
      Class<?> rawType) {
    Object parsed = parseIdentity(wireIdentifier, rawType);
    try {
      JavaType identifierType = identifierProperty.type();
      Object converted =
          propertyScoped.convert(
              beanType, identifierProperty.externalName(), identifierType, identifierType, parsed);
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
      ReadMappingProperty property,
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
      return convertViaJackson(property, rawValue, targetType, beanType);
    } catch (JsonApiMappingException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          rawType,
          attributeLocation,
          "Failed to convert attribute '" + property.logicalName() + "' for " + rawType.getName(),
          e);
    }
  }

  /**
   * Uses the property's fully-contextualized deserializer unless the caller unwrapped a wrapper
   * type, in which case the value converts against {@code targetType} directly.
   */
  private @Nullable Object convertViaJackson(
      ReadMappingProperty property,
      @Nullable Object rawValue,
      JavaType targetType,
      JavaType beanType) {
    return propertyScoped.convert(
        beanType, property.externalName(), property.type(), targetType, rawValue);
  }

  /**
   * Converts one whole-meta value atomically on the low-level path. Reuses the same property-scoped
   * Jackson authority as attribute conversion, but the caller supplies the location-specific meta
   * location so failures never surface an attribute-oriented pointer.
   */
  @Nullable Object convertWholeMeta(
      ReadMappingProperty property,
      @Nullable Object rawValue,
      JavaType declaredType,
      JavaType beanType,
      MappingLocation metaLocation,
      Class<?> rawType) {
    try {
      return convertViaJackson(property, rawValue, declaredType, beanType);
    } catch (JsonApiMappingException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          rawType,
          metaLocation,
          "Failed to convert the meta value at '" + metaLocation + "'",
          e);
    }
  }

  /** Converts linkage for a typed PATCH DTO without coercing the complete inner target type yet. */
  @Nullable Object convertRelationshipForPatchDto(
      MappingProperty property, RelationshipData data, JavaType targetType) {
    return convertRelationshipLinkage(property, data, targetType);
  }

  private @Nullable Object convertRelationshipLinkage(
      MappingProperty property, RelationshipData data, JavaType targetType) {
    JavaType mappingType =
        MappingTypeSupport.targetMappingType(targetType, mapper.getTypeFactory());
    RelationshipLinkageMapper linkageMapper =
        RelationshipLinkageSupport.selectLinkageMapper(targetType, property, linkageMappers);
    return RelationshipLinkageSupport.convertLinkage(
        property, data, linkageMapper, mappingType, mapper);
  }

  /** Applies final native coercion of one orchestrated relationship value to its declared type. */
  @Nullable Object coerceRelationship(ReadMappingProperty property, @Nullable Object intermediate) {
    JavaType targetType = property.type();
    if (alreadyConverted(intermediate, targetType)) {
      return intermediate;
    }
    try {
      return mapper.convertValue(intermediate, targetType);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET,
          targetType.getRawClass(),
          RelationshipMetaSupport.relationshipLocation(property),
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
    if (MappingTypeSupport.isToManyType(targetType)) {
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
      JavaType contentType = MappingTypeSupport.resolveContentType(targetType);
      if (contentType == null) {
        return false;
      }
      Object first = list.getFirst();
      return contentType.getRawClass().isInstance(first);
    }
    return targetType.getRawClass().isInstance(intermediate);
  }

  private static boolean needsCollectionCoercion(Object intermediate, JavaType targetType) {
    if (!MappingTypeSupport.isToManyType(targetType)) {
      return false;
    }
    if (targetType.isArrayType()) {
      return true;
    }
    return targetType.isTypeOrSubTypeOf(Set.class) && !(intermediate instanceof Set);
  }

  /** Unwraps a single exact {@code PatchPresence<T>} wrapper and returns other types as-is. */
  static JavaType unwrapPatchPresence(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1
        ? type.containedType(0)
        : type;
  }
}
