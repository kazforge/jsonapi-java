package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whole-meta target-shape rules for the write mapping role (ADR-015).
 *
 * <p>The mapping cache/resolver is deliberately kind-agnostic; the write mapping validates its
 * role's wrapper chain against these rules. Read/write mapping allows at most one {@link Optional}
 * wrapper around a Bean / {@link Map} / {@link Object} target. Identifier meta on an opt-in {@code
 * RelationshipLinkage<T, M>} follows the same object-shape rule for {@code M} (ADR-017).
 *
 * <p>Whether an effective target is a legal whole-meta object target is decided by Jackson, not a
 * manually maintained scalar taxonomy: after rejecting primitives, containers, and the already
 * unwrapped {@link Optional}/{@code PatchPresence} raws, a target is valid iff its root
 * deserializer reports the POJO {@link LogicalType} — the same structured-value signal ADR-014's
 * bean boundary is built on, read through root-level decoration. This accepts records, POJOs,
 * constructor-bound beans, and root-polymorphic POJOs whose deserializer is wrapped by a {@code
 * TypeDeserializer} (concrete or abstract polymorphic types), while rejecting JDK scalars ({@code
 * String}, {@code Character}, {@code Boolean}, {@code Number}, {@code java.time}, {@code
 * java.math}, {@link java.util.UUID}, {@link java.net.URI}/{@link java.net.URL}), enums,
 * containers, and custom scalar deserializers (which report a non-POJO or null logical type).
 * {@code Object} and {@link Map}-like targets are valid and atomic.
 */
final class WholeMetaTarget {

  private final JsonMapper mapper;
  private final Map<JavaType, Boolean> beanShapeCache = new ConcurrentHashMap<>();

  WholeMetaTarget(JsonMapper mapper) {
    this.mapper = mapper;
  }

  /** Read/write mapping rule: at most one Optional, then Bean/Map/Object. */
  boolean invalidReadWriteTarget(JavaType declared) {
    JavaType effective = unwrapOptional(declared);
    if (isOptional(effective)) {
      return true;
    }
    return !isObjectCompatible(effective);
  }

  /**
   * Validates every declared whole-meta target of {@code mapping} for the write mapping role,
   * throwing {@link MappingDiagnostic#INVALID_META_TARGET} at the property's resource-relative wire
   * location when a declared target is not Bean / Map / Object with at most one {@link Optional}
   * wrapper. The mapping cache/resolver stays kind-agnostic; the consuming entry point invokes this
   * shared rule itself (ADR-015).
   */
  void validateReadWriteTargets(ResourceMapping mapping, Class<?> rawType) {
    MappingProperty resourceMeta = mapping.resourceMeta();
    if (resourceMeta != null
        && invalidReadWriteTarget(resourceMeta.definition().getPrimaryType())) {
      throw invalidTarget(
          "Resource meta", resourceMeta, rawType, RelationshipMetaSupport.resourceMetaLocation());
    }
    for (MappingProperty property : mapping.relationshipMetaProperties()) {
      if (invalidReadWriteTarget(property.definition().getPrimaryType())) {
        throw invalidTarget(
            "Relationship meta",
            property,
            rawType,
            RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
      }
    }
    validateRelationshipLinkageMeta(mapping.relationships(), rawType);
  }

  void validateRelationshipLinkageMeta(
      List<? extends MappingPropertyView> relationships, Class<?> rawType) {
    for (MappingPropertyView property : relationships) {
      JavaType linkageType = RelationshipLinkageSupport.linkageJavaType(property.type());
      if (linkageType == null) {
        continue;
      }
      MappingLocation location =
          IdentifierMetaSupport.identifierMetaLocation(property.jsonapiName());
      if (linkageType.containedTypeCount() < 2
          || linkageType.getBindings().isEmpty()
          || RelationshipLinkageSupport.isLinkageType(
              RelationshipLinkageSupport.unwrapOptionalType(
                  RelationshipLinkageSupport.linkageTargetType(linkageType)))) {
        throw invalidIdentifierMetaTarget(property, rawType, location);
      }
      JavaType metaType = RelationshipLinkageSupport.linkageMetaType(linkageType);
      if (invalidReadWriteTarget(metaType)) {
        throw invalidIdentifierMetaTarget(property, rawType, location);
      }
    }
  }

  private static JsonApiMappingException invalidIdentifierMetaTarget(
      MappingPropertyView property, Class<?> rawType, MappingLocation location) {
    return new JsonApiMappingException(
        MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET,
        rawType,
        location,
        "Relationship '"
            + property.logicalName()
            + "' RelationshipLinkage meta type must be a Bean, Map, or Object (with at most one"
            + " Optional wrapper) on "
            + rawType.getName());
  }

  private static JsonApiMappingException invalidTarget(
      String kind, MappingPropertyView property, Class<?> rawType, MappingLocation metaLocation) {
    return new JsonApiMappingException(
        MappingDiagnostic.INVALID_META_TARGET,
        rawType,
        metaLocation,
        kind
            + " property '"
            + property.logicalName()
            + "' must be a Bean, Map, or Object (with at most one Optional wrapper) on "
            + rawType.getName());
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
   *
   * <p>The deserializer is resolved through a mapper-config-bound context created from the mapper's
   * own {@code DefaultDeserializationContext}: Jackson 2 contexts are per-read, so the mapper's
   * template context is instantiated against its deserialization config without a parser, the same
   * route {@code ObjectMapper.canDeserialize} uses.
   */
  private boolean isObjectShaped(JavaType type) {
    Boolean cached = beanShapeCache.get(type);
    if (cached != null) {
      return cached;
    }
    boolean shaped = computeObjectShaped(type);
    beanShapeCache.put(type, shaped);
    return shaped;
  }

  private boolean computeObjectShaped(JavaType type) {
    try {
      DeserializationConfig config = mapper.getDeserializationConfig();
      DeserializationContext context =
          ((com.fasterxml.jackson.databind.deser.DefaultDeserializationContext)
                  mapper.getDeserializationContext())
              .createInstance(config, null, null);
      JsonDeserializer<?> deserializer = context.findRootValueDeserializer(type);
      return deserializer != null && deserializer.logicalType() == LogicalType.POJO;
    } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
      throw new IllegalStateException("Failed to resolve a deserializer for " + type, e);
    }
  }

  private static boolean isOptional(JavaType type) {
    return type.getRawClass() == Optional.class && type.containedTypeCount() == 1;
  }

  private static JavaType unwrapOptional(JavaType type) {
    return isOptional(type) ? type.containedType(0) : type;
  }
}
