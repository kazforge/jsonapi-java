package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.core.model.RelationshipData;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.internal.mapping.IdentifierMetaSupport;
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage;
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence;
import io.github.kazemek.jsonapi.jackson3.RelationshipLinkageMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * Shared relationship linkage rules for flat DTO binding and presence-aware PATCH: cardinality
 * checks, target-class resolution, opt-in {@link RelationshipLinkage} unwrap/wrap, built-in {@link
 * ResourceIdentifier} conversion that preserves identifier meta, and custom linkage mappers.
 */
final class RelationshipLinkageSupport {

  private RelationshipLinkageSupport() {}

  static boolean isLinkageType(JavaType type) {
    return type.getRawClass() == RelationshipLinkage.class;
  }

  /**
   * Returns the {@link RelationshipLinkage} JavaType of a relationship property, or {@code null}
   * when the property is an ordinary target. Looks through one {@link Optional} and, for to-many
   * properties, through the collection/array content type.
   */
  static @Nullable JavaType linkageJavaType(JavaType propertyType) {
    JavaType unwrapped = unwrapTransportWrappers(propertyType);
    if (isLinkageType(unwrapped)) {
      return unwrapped;
    }
    if (DomainResourceWriter.isToManyType(unwrapped)) {
      JavaType content = DomainResourceWriter.resolveContentType(unwrapped);
      if (content != null) {
        JavaType contentUnwrapped = unwrapOptionalType(content);
        if (isLinkageType(contentUnwrapped)) {
          return contentUnwrapped;
        }
      }
    }
    return null;
  }

  static JavaType linkageTargetType(JavaType linkageType) {
    return linkageType.containedType(0);
  }

  static JavaType linkageMetaType(JavaType linkageType) {
    return linkageType.containedType(1);
  }

  /**
   * The JavaType against which ordinary target conversion runs. For a wrapper property this is
   * {@code T} (or a collection/array of {@code T}); otherwise the original property type.
   */
  static JavaType targetMappingType(JavaType propertyType, TypeFactory typeFactory) {
    JavaType unwrapped = unwrapTransportWrappers(propertyType);
    JavaType linkageType = linkageJavaType(unwrapped);
    if (linkageType == null) {
      return propertyType;
    }
    JavaType target = linkageTargetType(linkageType);
    if (!DomainResourceWriter.isToManyType(unwrapped)) {
      return target;
    }
    if (unwrapped.isArrayType()) {
      return typeFactory.constructArrayType(target);
    }
    Class<?> raw = unwrapped.getRawClass();
    if (Set.class.isAssignableFrom(raw)) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Class<? extends Collection> setType = (Class<? extends Collection>) raw;
      return typeFactory.constructCollectionType(setType, target);
    }
    return typeFactory.constructCollectionType(List.class, target);
  }

  private static Class<?> resolveTargetClass(
      JavaType propertyType, boolean toMany, MappingPropertyView property) {
    JavaType linkageType = linkageJavaType(propertyType);
    if (linkageType != null) {
      return linkageTargetType(linkageType).getRawClass();
    }
    if (toMany) {
      JavaType contentType = DomainResourceWriter.resolveContentType(propertyType);
      if (contentType == null) {
        throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET,
            rawTypeOf(property),
            relationshipLocation(property),
            "Cannot resolve collection content type for relationship '"
                + property.logicalName()
                + "'");
      }
      return contentType.getRawClass();
    }
    return unwrapOptionalType(propertyType).getRawClass();
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
    boolean toMany = DomainResourceWriter.isToManyType(unwrapTransportWrappers(propertyType));
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
    JavaType linkageType = linkageJavaType(property.type());
    boolean propertyToMany =
        DomainResourceWriter.isToManyType(unwrapTransportWrappers(property.type()));
    if (linkageType != null && propertyToMany) {
      return wrapToManyOccurrences(property, data, linkageMapper, jacksonMapper, linkageType);
    }
    boolean mappingToMany = DomainResourceWriter.isToManyType(mappingType);
    JavaType mapperTargetType = mappingToMany ? mappingType : unwrapOptionalType(mappingType);
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
          relationshipLocation(property),
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
            singleIdentifier(data), linkageMetaType(linkageType), jacksonMapper, property, -1));
  }

  private static Object wrapToManyOccurrences(
      MappingPropertyView property,
      RelationshipData data,
      @Nullable RelationshipLinkageMapper linkageMapper,
      JsonMapper jacksonMapper,
      JavaType linkageType) {
    boolean empty = validateCardinality(property, data, true);
    List<ResourceIdentifier> identifiers =
        switch (data) {
          case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> ids) -> ids;
          default -> List.of();
        };
    if (empty) {
      return List.of();
    }
    JavaType targetType = linkageTargetType(linkageType);
    JavaType metaType = linkageMetaType(linkageType);
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
    return switch (data) {
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) -> identifier;
      default -> null;
    };
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
    Meta meta = identifier.meta();
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

  static JavaType unwrapTransportWrappers(JavaType type) {
    JavaType current = type;
    if (current.getRawClass() == PatchPresence.class && current.containedTypeCount() == 1) {
      current = current.containedType(0);
    }
    return unwrapOptionalType(current);
  }

  static JavaType unwrapOptionalType(JavaType type) {
    if (type.isTypeOrSubTypeOf(Optional.class) && type.containedTypeCount() == 1) {
      return type.containedType(0);
    }
    return type;
  }

  static Class<?> rawTypeOf(MappingPropertyView property) {
    return property.type().getRawClass();
  }

  static JsonApiMappingException unsupportedRelationshipTarget(
      MappingPropertyView property, Class<?> targetClass) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_TARGET,
        rawTypeOf(property),
        relationshipLocation(property),
        "Relationship '"
            + property.logicalName()
            + "' targets unsupported type "
            + targetClass.getName());
  }

  static MappingLocation relationshipLocation(MappingPropertyView property) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), "data");
  }

  private static JsonApiMappingException cardinalityMismatch(
      MappingPropertyView property, String detail) {
    return new JsonApiMappingException(
        MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH,
        rawTypeOf(property),
        relationshipLocation(property),
        "Cardinality mismatch for relationship '" + property.logicalName() + "': " + detail);
  }
}
