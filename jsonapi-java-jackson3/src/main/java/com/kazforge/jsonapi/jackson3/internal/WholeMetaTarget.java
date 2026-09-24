package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.internal.IdentifierMetaSupport;
import com.kazforge.jsonapi.mapping.internal.PatchProperty;
import com.kazforge.jsonapi.mapping.internal.PatchResourceDefinition;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Whole-meta target-shape rules shared by the entry points that consume whole-meta mappings.
 *
 * <p>The mapping cache/resolver is deliberately kind-agnostic; each consuming entry point validates
 * its own role's wrapper chain against these rules. Read/write and low-level domain mappings allow
 * at most one {@link Optional} wrapper around a Bean / {@link Map} / {@link Object} target; typed
 * PATCH DTOs allow exactly one {@code PatchPresence<T>} wrapper and at most one {@link Optional}
 * inside it. Identifier meta on an opt-in {@code RelationshipLinkage<T, M>} follows the same
 * object-shape rule for {@code M}.
 *
 * <p>Whether an effective target is a legal whole-meta object target is decided by Jackson, not a
 * manually maintained scalar taxonomy: after rejecting primitives, containers, and the already
 * unwrapped {@link Optional}/{@code PatchPresence} raws, a target is valid iff its root
 * deserializer reports the POJO {@link LogicalType}, the same structured-value signal used by the
 * bean boundary, read through root-level decoration. This accepts records, POJOs, constructor-bound
 * beans, and root-polymorphic POJOs whose deserializer is wrapped by a {@code TypeDeserializer}
 * (concrete or abstract {@code @JsonTypeInfo} types), while rejecting JDK scalars ({@code String},
 * {@code Character}, {@code Boolean}, {@code Number}, {@code java.time}, {@code java.math}, {@link
 * java.util.UUID}, {@link java.net.URI}/{@link java.net.URL}), enums, containers, and custom scalar
 * deserializers (which report a non-POJO or null logical type). {@code Object} and {@link Map}-like
 * targets are valid and atomic. Presence-aware recursion is a separate {@link
 * StructuredValueBinder} decision and is never required here.
 */
final class WholeMetaTarget {

  private static final String RESOURCE_META_LABEL = "Resource meta";
  private static final String RELATIONSHIP_META_LABEL = "Relationship meta";

  private final JsonMapper mapper;
  private final Map<JavaType, Boolean> beanShapeCache = new ConcurrentHashMap<>();

  WholeMetaTarget(JsonMapper mapper) {
    this.mapper = mapper;
  }

  /** Read/write and low-level domain-mapping rule: at most one Optional, then Bean/Map/Object. */
  boolean invalidReadWriteTarget(JavaType declared) {
    JavaType effective = unwrapOptional(declared);
    if (isOptional(effective)) {
      return true;
    }
    return !isObjectCompatible(effective);
  }

  /**
   * Validates every declared whole-meta target of {@code mapping} for the read/write and low-level
   * domain-mapping roles, throwing {@link MappingDiagnostic#INVALID_META_TARGET} at the property's
   * resource-relative wire location when a declared target is not Bean / Map / Object with at most
   * one {@link Optional} wrapper. The mapping cache/resolver stays kind-agnostic; each consuming
   * entry point invokes this shared rule itself.
   */
  void validateReadWriteTargets(ResourceMapping mapping, Class<?> rawType) {
    MappingProperty resourceMeta = mapping.resourceMeta();
    if (resourceMeta != null
        && invalidReadWriteTarget(resourceMeta.definition().getPrimaryType())) {
      throw invalidTarget(
          RESOURCE_META_LABEL,
          resourceMeta.logicalName(),
          rawType,
          RelationshipMetaSupport.resourceMetaLocation());
    }
    for (MappingProperty property : mapping.relationshipMetaProperties()) {
      if (invalidReadWriteTarget(property.definition().getPrimaryType())) {
        throw invalidTarget(
            RELATIONSHIP_META_LABEL,
            property.logicalName(),
            rawType,
            RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
      }
    }
    validateRelationshipLinkageMeta(mapping.relationships(), rawType);
  }

  /** Validates whole-meta targets using effective deserialization-side property types. */
  void validateReadWriteTargets(ReadResourceMapping mapping, Class<?> rawType) {
    ReadMappingProperty resourceMeta = mapping.resourceMeta();
    if (resourceMeta != null && invalidReadWriteTarget(resourceMeta.type())) {
      throw invalidTarget(
          RESOURCE_META_LABEL,
          resourceMeta.logicalName(),
          rawType,
          RelationshipMetaSupport.resourceMetaLocation());
    }
    for (ReadMappingProperty property : mapping.relationshipMetaProperties()) {
      if (invalidReadWriteTarget(property.type())) {
        throw invalidTarget(
            RELATIONSHIP_META_LABEL,
            property.logicalName(),
            rawType,
            RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
      }
    }
    validateRelationshipLinkageMeta(mapping.relationships(), rawType);
  }

  /**
   * Validates the declared whole-meta targets of the dedicated low-level PATCH projection,
   * considering only effective/bindable inbound properties. A serialization-only declaration has no
   * effective deserialization target, so it contributes no declared target and cannot block a PATCH
   * that does not supply it; a supplied member is failed by the shared binder's bindability check
   * at its member location instead of being validated from a serialization-side fallback type.
   */
  void validateDeclaredPatchTargets(
      PatchResourceDefinition<ReadMappingProperty> definition, Class<?> rawType) {
    PatchProperty<ReadMappingProperty> resourceMeta = definition.resourceMeta();
    if (resourceMeta != null
        && resourceMeta.bindable()
        && invalidReadWriteTarget(resourceMeta.token().type())) {
      throw invalidTarget(
          RESOURCE_META_LABEL,
          resourceMeta.logicalName(),
          rawType,
          RelationshipMetaSupport.resourceMetaLocation());
    }
    for (PatchProperty<ReadMappingProperty> property : definition.relationshipMetaProperties()) {
      if (property.bindable() && invalidReadWriteTarget(property.token().type())) {
        throw invalidTarget(
            RELATIONSHIP_META_LABEL,
            property.logicalName(),
            rawType,
            RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
      }
    }
    for (PatchProperty<ReadMappingProperty> property : definition.relationships()) {
      if (property.bindable()) {
        validateRelationshipLinkageMeta(List.of(property.token()), rawType);
      }
    }
  }

  void validateRelationshipLinkageMeta(
      List<? extends MappingPropertyView> relationships, Class<?> rawType) {
    for (MappingPropertyView property : relationships) {
      JavaType linkageType = MappingTypeSupport.linkageJavaType(property.type());
      if (linkageType == null) {
        continue;
      }
      MappingLocation location =
          IdentifierMetaSupport.identifierMetaLocation(property.jsonapiName());
      if (linkageType.containedTypeCount() < 2
          || linkageType.getBindings().isEmpty()
          || MappingTypeSupport.isLinkageType(
              MappingTypeSupport.unwrapOptionalType(
                  MappingTypeSupport.linkageTargetType(linkageType)))) {
        throw invalidIdentifierMetaTarget(property.logicalName(), rawType, location);
      }
      JavaType metaType = MappingTypeSupport.linkageMetaType(linkageType);
      if (invalidReadWriteTarget(metaType)) {
        throw invalidIdentifierMetaTarget(property.logicalName(), rawType, location);
      }
    }
  }

  private static JsonApiMappingException invalidIdentifierMetaTarget(
      String logicalName, Class<?> rawType, MappingLocation location) {
    return new JsonApiMappingException(
        MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET,
        rawType,
        location,
        "Relationship '"
            + logicalName
            + "' RelationshipLinkage meta type must be a Bean, Map, or Object (with at most one"
            + " Optional wrapper) on "
            + rawType.getName());
  }

  private static JsonApiMappingException invalidTarget(
      String kind, String logicalName, Class<?> rawType, MappingLocation metaLocation) {
    return new JsonApiMappingException(
        MappingDiagnostic.INVALID_META_TARGET,
        rawType,
        metaLocation,
        kind
            + " property '"
            + logicalName
            + "' must be a Bean, Map, or Object (with at most one Optional wrapper) on "
            + rawType.getName());
  }

  /**
   * Typed PATCH DTO rule: exactly one PatchPresence, at most one Optional inside, then
   * Bean/Map/Object.
   */
  boolean validTypedPatchTarget(JavaType declared) {
    if (!isPatchPresence(declared)) {
      return false;
    }
    JavaType inner = declared.containedType(0);
    JavaType effective = isOptional(inner) ? inner.containedType(0) : inner;
    if (isOptional(effective)) {
      return false;
    }
    return isObjectCompatible(effective);
  }

  private boolean isObjectCompatible(JavaType type) {
    if (type.isPrimitive() || type.isArrayType() || type.isCollectionLikeType()) {
      return false;
    }
    if (type.isMapLikeType()) {
      return true;
    }
    Class<?> raw = type.getRawClass();
    if (raw == Object.class) {
      return true;
    }
    if (raw == Optional.class
        || raw == PatchPresence.class
        || raw == PatchPresence.Present.class
        || raw == PatchPresence.Omitted.class) {
      return false;
    }
    return isObjectShaped(type);
  }

  /**
   * True when Jackson's effective deserializer for the type reports the POJO {@link LogicalType}.
   * This reads through root-level {@code TypeDeserializer} decoration ({@code
   * TypeWrappedDeserializer} delegates {@code logicalType()} to its wrapped bean deserializer) and
   * abstract POJO placeholders ({@code AbstractDeserializer}), so a root-polymorphic whole-meta
   * POJO is not rejected merely because its root deserializer is decorated. Custom scalar
   * deserializers report a non-POJO or {@code null} logical type and stay rejected.
   */
  private boolean isObjectShaped(JavaType type) {
    return beanShapeCache.computeIfAbsent(
        type,
        ignored -> {
          DeserializationContext context = mapper._deserializationContext();
          ValueDeserializer<?> deserializer = context.findRootValueDeserializer(type);
          return deserializer != null && deserializer.logicalType() == LogicalType.POJO;
        });
  }

  private static boolean isPatchPresence(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1;
  }

  private static boolean isOptional(JavaType type) {
    return type.getRawClass() == Optional.class && type.containedTypeCount() == 1;
  }

  private static JavaType unwrapOptional(JavaType type) {
    return isOptional(type) ? type.containedType(0) : type;
  }
}
