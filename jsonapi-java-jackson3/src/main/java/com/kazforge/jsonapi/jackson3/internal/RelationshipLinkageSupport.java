package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.mapping.internal.IdentifierMetaSupport;
import com.kazforge.jsonapi.mapping.internal.ReadRelationshipShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * Adapter-local native relationship-linkage support: declared-shape resolution and
 * configured-mapper selection, configured mapper invocation, and identifier-meta conversion for
 * flat read and low-level PATCH binding, plus the cardinality checks, opt-in {@link
 * RelationshipLinkage} unwrap/wrap, and built-in {@link ResourceIdentifier} conversion still used
 * by the typed {@code PatchPresence} DTO path.
 *
 * <p>Ordinary flat reads and low-level PATCH binding no longer own cardinality, null/empty
 * short-circuiting, wrapper occurrence orchestration, or identifier-meta sequencing; those live
 * once in the shared {@code com.kazforge.jsonapi.mapping.internal.RelationshipLinkageBinder}, and
 * this class is reached only for the native operations above. Only the typed {@code PatchPresence}
 * DTO path still uses its own {@link #convertLinkage} orchestration.
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
   * {@link ResourceIdentifier} conversion applies. Read binding and PATCH conversion share this so
   * they cannot resolve different mappers for the same declared type.
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
   * Resolves the neutral read shape of one mapped relationship property for the shared reader. This
   * is the lazy native edge for read binding: target/type resolution and mapper selection happen
   * here, so an unsupported or unresolvable target fails before the shared cardinality and
   * null/empty short-circuit checks. A {@link ResourceIdentifier} target is {@code Direct}; any
   * other registered target is {@code Mapped} with the mapping token the mapper expects; an opt-in
   * {@code RelationshipLinkage} property is {@code Wrapped} over its target's own direct/mapped
   * shape.
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
   * Invokes the configured mapper for one non-empty, cardinality-valid mapped linkage branch. The
   * shared reader owns cardinality, short-circuiting, and wrapper occurrence orchestration; this
   * native edge resolves the registered mapper and translates mapper failures into the stable
   * {@link MappingDiagnostic#LINKAGE_MAPPING_FAILED} diagnostic at the relationship's data
   * location.
   */
  static @Nullable Object mapLinkage(
      RelationshipData data,
      JavaType target,
      MappingPropertyView property,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    RelationshipLinkageMapper mapper =
        Objects.requireNonNull(
            selectLinkageMapper(property.type(), property, linkageMappers), "linkageMapper");
    return invokeLinkageMapper(mapper, data, target, property);
  }

  /**
   * Validates linkage shape against the property's cardinality, throwing {@link
   * MappingDiagnostic#RELATIONSHIP_CARDINALITY_MISMATCH} for illegal combinations. Returns whether
   * the linkage denotes an empty value ({@code null} on to-one, empty collection on to-many).
   */
  static boolean validateCardinality(
      MappingPropertyView property, RelationshipData data, boolean toMany) {
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> {
        if (toMany) {
          throw cardinalityMismatch(property, "null linkage on to-many relationship");
        }
        yield true;
      }
      case RelationshipData.SingleLinkage ignored -> {
        if (toMany) {
          throw cardinalityMismatch(property, "single linkage on to-many relationship");
        }
        yield false;
      }
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        boolean empty = identifiers.isEmpty();
        if (!toMany) {
          throw cardinalityMismatch(
              property,
              empty
                  ? "empty collection linkage on to-one relationship"
                  : "collection linkage on to-one relationship");
        }
        yield empty;
      }
    };
  }

  /**
   * Converts relationship linkage to the property value, wrapping {@link RelationshipLinkage}
   * occurrences when the property opts in. To-many wrappers map each identifier to {@code T} before
   * attaching that identifier's meta, so a collection-level custom mapper cannot reorder or resize
   * the association.
   */
  static @Nullable Object convertLinkage(
      MappingPropertyView property,
      RelationshipData data,
      @Nullable RelationshipLinkageMapper linkageMapper,
      JavaType mappingType,
      JsonMapper jacksonMapper) {
    JavaType linkageType = MappingTypeSupport.linkageJavaType(property.type());
    boolean propertyToMany =
        MappingTypeSupport.isToManyType(
            MappingTypeSupport.unwrapTransportWrappers(property.type()));
    if (linkageType != null && propertyToMany) {
      return wrapToManyOccurrences(property, data, linkageMapper, jacksonMapper, linkageType);
    }
    boolean mappingToMany = MappingTypeSupport.isToManyType(mappingType);
    JavaType mapperTargetType =
        mappingToMany ? mappingType : MappingTypeSupport.unwrapOptionalType(mappingType);
    Object converted =
        linkageMapper == null
            ? builtInLinkage(property, data, mappingToMany)
            : mappedLinkage(property, data, mappingToMany, linkageMapper, mapperTargetType);
    if (linkageType == null) {
      return converted;
    }
    return wrapToOne(property, data, converted, jacksonMapper, linkageType);
  }

  private static @Nullable Object builtInLinkage(
      MappingPropertyView property, RelationshipData data, boolean toMany) {
    boolean empty = validateCardinality(property, data, toMany);
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> null;
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) ->
          IdentifierMetaSupport.copyLinkageIdentifier(identifier);
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        if (empty) {
          yield List.of();
        }
        List<Object> values = new ArrayList<>(identifiers.size());
        for (ResourceIdentifier identifier : identifiers) {
          values.add(IdentifierMetaSupport.copyLinkageIdentifier(identifier));
        }
        yield values;
      }
    };
  }

  private static @Nullable Object mappedLinkage(
      MappingPropertyView property,
      RelationshipData data,
      boolean toMany,
      RelationshipLinkageMapper linkageMapper,
      JavaType mapperTargetType) {
    boolean empty = validateCardinality(property, data, toMany);
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> null;
      case RelationshipData.SingleLinkage single ->
          invokeLinkageMapper(linkageMapper, single, mapperTargetType, property);
      case RelationshipData.IdentifierCollectionLinkage collection -> {
        if (empty) {
          yield List.of();
        }
        yield invokeLinkageMapper(linkageMapper, collection, mapperTargetType, property);
      }
    };
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

  private static @Nullable Object wrapToOne(
      MappingPropertyView property,
      RelationshipData data,
      @Nullable Object converted,
      JsonMapper jacksonMapper,
      JavaType linkageType) {
    if (converted == null) {
      return null;
    }
    return new RelationshipLinkage<>(
        converted,
        convertIdentifierMeta(
            singleIdentifier(data),
            MappingTypeSupport.linkageMetaType(linkageType),
            jacksonMapper,
            property,
            -1));
  }

  private static Object wrapToManyOccurrences(
      MappingPropertyView property,
      RelationshipData data,
      @Nullable RelationshipLinkageMapper linkageMapper,
      JsonMapper jacksonMapper,
      JavaType linkageType) {
    boolean empty = validateCardinality(property, data, true);
    List<ResourceIdentifier> identifiers =
        data instanceof RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> ids)
            ? ids
            : List.of();
    if (empty) {
      return List.of();
    }
    JavaType targetType = MappingTypeSupport.linkageTargetType(linkageType);
    JavaType metaType = MappingTypeSupport.linkageMetaType(linkageType);
    List<Object> wrapped = new ArrayList<>(identifiers.size());
    for (int index = 0; index < identifiers.size(); index++) {
      ResourceIdentifier identifier = identifiers.get(index);
      Object target =
          linkageMapper == null
              ? IdentifierMetaSupport.copyLinkageIdentifier(identifier)
              : invokeLinkageMapper(
                  linkageMapper,
                  new RelationshipData.SingleLinkage(identifier),
                  targetType,
                  property);
      if (target == null) {
        throw new JsonApiMappingException(
            MappingDiagnostic.LINKAGE_MAPPING_FAILED,
            rawTypeOf(property),
            MappingLocation.of(
                JsonApiMembers.RELATIONSHIPS,
                property.jsonapiName(),
                JsonApiMembers.DATA,
                Integer.toString(index)),
            "Relationship linkage mapper returned null for relationship '"
                + property.logicalName()
                + "'");
      }
      wrapped.add(
          new RelationshipLinkage<>(
              target, convertIdentifierMeta(identifier, metaType, jacksonMapper, property, index)));
    }
    return wrapped;
  }

  private static @Nullable ResourceIdentifier singleIdentifier(RelationshipData data) {
    return data instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)
        ? identifier
        : null;
  }

  private static @Nullable Object convertIdentifierMeta(
      @Nullable ResourceIdentifier identifier,
      JavaType metaType,
      JsonMapper mapper,
      MappingPropertyView property,
      int index) {
    if (identifier == null || identifier.meta() == null) {
      return null;
    }
    return convertIdentifierMeta(
        Objects.requireNonNull(identifier.meta()), metaType, mapper, property, index);
  }

  /**
   * Converts one present identifier {@link Meta} to the declared identifier-meta token. The shared
   * reader owns presence, occurrence index, and the identifier-meta location; native conversion
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

  private static JsonApiMappingException cardinalityMismatch(
      MappingPropertyView property, String detail) {
    return new JsonApiMappingException(
        MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH,
        rawTypeOf(property),
        RelationshipMetaSupport.relationshipLocation(property),
        "Cardinality mismatch for relationship '" + property.logicalName() + "': " + detail);
  }
}
