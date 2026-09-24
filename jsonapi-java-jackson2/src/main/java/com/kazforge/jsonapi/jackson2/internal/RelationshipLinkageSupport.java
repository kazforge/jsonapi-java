package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.internal.IdentifierMetaSupport;
import com.kazforge.jsonapi.mapping.internal.ReadRelationshipShape;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Adapter-local native relationship-linkage support: declared-shape resolution and
 * configured-mapper selection, configured mapper invocation, and identifier-meta conversion for
 * flat read, low-level PATCH, and typed {@code PatchPresence} DTO binding.
 *
 * <p>All three paths delegate cardinality, null/empty short-circuiting, direct identifier copying,
 * opt-in wrapper occurrence orchestration, per-occurrence target/meta pairing, and the shared
 * cardinality and linkage-mapping diagnostics once to the neutral {@code
 * com.kazforge.jsonapi.mapping.internal.RelationshipLinkageBinder}. This class is reached only for
 * the native operations above. The typed DTO path resolves shape and mapper selection against the
 * unwrapped {@code PatchPresence} inner type through the type-parameterized {@code mapLinkage}
 * overload while diagnostics keep the native property.
 */
final class RelationshipLinkageSupport {

  private RelationshipLinkageSupport() {}

  private static Class<?> resolveTargetClass(
      JavaType propertyType, boolean toMany, MappingPropertyView property) {
    JavaType linkageType = MappingTypeSupport.linkageJavaType(propertyType);
    if (linkageType != null) {
      return MappingTypeSupport.linkageTargetType(linkageType).getRawClass();
    }
    if (toMany) {
      JavaType contentType = MappingTypeSupport.resolveContentType(propertyType);
      if (contentType == null) {
        throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET,
            rawTypeOf(property),
            RelationshipMetaSupport.relationshipLocation(property),
            "Cannot resolve collection content type for relationship '"
                + property.logicalName()
                + "'");
      }
      return contentType.getRawClass();
    }
    return MappingTypeSupport.unwrapOptionalType(propertyType).getRawClass();
  }

  /**
   * Returns the registered mapper for the relationship target, or {@code null} when the built-in
   * resource-identifier conversion applies. Read binding, low-level PATCH, and typed PATCH DTO
   * conversion share this so they cannot resolve different mappers for the same declared type.
   */
  static @Nullable RelationshipLinkageMapper selectLinkageMapper(
      JavaType propertyType,
      MappingPropertyView property,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    boolean toMany =
        MappingTypeSupport.isToManyType(MappingTypeSupport.unwrapTransportWrappers(propertyType));
    Class<?> targetClass = resolveTargetClass(propertyType, toMany, property);
    if (targetClass == ResourceIdentifier.class) {
      return null;
    }
    RelationshipLinkageMapper mapper = linkageMappers.get(targetClass);
    if (mapper == null) {
      throw unsupportedRelationshipTarget(property, targetClass);
    }
    return mapper;
  }

  /**
   * Resolves the neutral read shape of one mapped relationship property for the shared binder. This
   * is the lazy native edge for read binding: target/type resolution and mapper selection happen
   * here, so an unsupported or unresolvable target fails before the shared cardinality and
   * null/empty short-circuit checks. A built-in identifier target is {@code Direct}; any other
   * registered target is {@code Mapped} with the mapping token the mapper expects; an opt-in {@code
   * RelationshipLinkage} property is {@code Wrapped} over its target's own direct/mapped shape.
   */
  static ReadRelationshipShape<JavaType> readRelationshipShape(
      JavaType propertyType,
      MappingPropertyView property,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers,
      TypeFactory typeFactory) {
    JavaType linkageType = MappingTypeSupport.linkageJavaType(propertyType);
    if (linkageType != null) {
      boolean toMany =
          MappingTypeSupport.isToManyType(MappingTypeSupport.unwrapTransportWrappers(propertyType));
      RelationshipLinkageMapper mapper =
          selectLinkageMapper(propertyType, property, linkageMappers);
      ReadRelationshipShape<JavaType> targetShape =
          mapper == null
              ? new ReadRelationshipShape.Direct<>(false)
              : new ReadRelationshipShape.Mapped<>(
                  false, MappingTypeSupport.linkageTargetType(linkageType));
      return new ReadRelationshipShape.Wrapped<>(
          toMany, MappingTypeSupport.linkageMetaType(linkageType), targetShape);
    }
    RelationshipLinkageMapper mapper = selectLinkageMapper(propertyType, property, linkageMappers);
    JavaType mappingType = MappingTypeSupport.targetMappingType(propertyType, typeFactory);
    boolean toMany = MappingTypeSupport.isToManyType(mappingType);
    JavaType target = toMany ? mappingType : MappingTypeSupport.unwrapOptionalType(mappingType);
    if (mapper == null) {
      return new ReadRelationshipShape.Direct<>(toMany);
    }
    return new ReadRelationshipShape.Mapped<>(toMany, target);
  }

  /**
   * Invokes the configured mapper for one non-empty, cardinality-valid mapped linkage branch,
   * resolving the registered mapper from the declared property type. The shared binder owns
   * cardinality, short-circuiting, and wrapper occurrence orchestration; this native edge
   * translates mapper failures into the stable {@link MappingDiagnostic#LINKAGE_MAPPING_FAILED}
   * diagnostic at the relationship's data location.
   */
  static @Nullable Object mapLinkage(
      RelationshipData data,
      JavaType target,
      MappingPropertyView property,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    return mapLinkage(data, target, property.type(), property, linkageMappers);
  }

  /**
   * Invokes the configured mapper for one non-empty, cardinality-valid mapped linkage branch,
   * resolving the registered mapper against {@code resolutionType}. The typed {@code PatchPresence}
   * DTO path passes the unwrapped inner type so mapper selection never sees the outer wrapper,
   * while {@code property} still supplies the native diagnostic raw type and locations.
   */
  static @Nullable Object mapLinkage(
      RelationshipData data,
      JavaType target,
      JavaType resolutionType,
      MappingPropertyView property,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    RelationshipLinkageMapper mapper =
        Objects.requireNonNull(
            selectLinkageMapper(resolutionType, property, linkageMappers), "linkageMapper");
    return invokeLinkageMapper(mapper, data, target, property);
  }

  private static @Nullable Object invokeLinkageMapper(
      RelationshipLinkageMapper linkageMapper,
      RelationshipData data,
      JavaType targetType,
      MappingPropertyView property) {
    try {
      return linkageMapper.map(data, targetType);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.LINKAGE_MAPPING_FAILED,
          rawTypeOf(property),
          RelationshipMetaSupport.relationshipLocation(property),
          "Relationship linkage mapper failed for relationship '" + property.logicalName() + "'",
          e);
    }
  }

  /**
   * Converts one present identifier {@link Meta} to the declared identifier-meta token. The shared
   * binder owns presence, occurrence index, and the identifier-meta location; native conversion
   * failures surface here as the stable {@link MappingDiagnostic#INVALID_META_TARGET} diagnostic at
   * that location.
   */
  static @Nullable Object convertIdentifierMeta(
      Meta meta, JavaType metaType, JsonMapper mapper, MappingPropertyView property, int index) {
    MappingLocation location =
        index < 0
            ? IdentifierMetaSupport.identifierMetaLocation(property.jsonapiName())
            : IdentifierMetaSupport.identifierMetaLocation(property.jsonapiName(), index);
    try {
      return mapper.convertValue(meta.members(), metaType);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          rawTypeOf(property),
          location,
          "Failed to convert identifier meta for relationship '" + property.logicalName() + "'",
          e);
    }
  }

  static Class<?> rawTypeOf(MappingPropertyView property) {
    return property.type().getRawClass();
  }

  static JsonApiMappingException unsupportedRelationshipTarget(
      MappingPropertyView property, Class<?> targetClass) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET,
        rawTypeOf(property),
        RelationshipMetaSupport.relationshipLocation(property),
        "Relationship '"
            + property.logicalName()
            + "' targets unsupported type "
            + targetClass.getName());
  }
}
