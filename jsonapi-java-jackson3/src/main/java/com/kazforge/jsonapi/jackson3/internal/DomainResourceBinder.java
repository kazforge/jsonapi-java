package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.internal.BasicReadResult;
import com.kazforge.jsonapi.mapping.internal.BasicResourceReader;
import com.kazforge.jsonapi.mapping.internal.ReadConstructionStart;
import com.kazforge.jsonapi.mapping.internal.ReadProperty;
import com.kazforge.jsonapi.mapping.internal.ReadRelationshipShape;
import com.kazforge.jsonapi.mapping.internal.ReadResourceBackend;
import com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Binds validated {@link ResourceObject} values to annotated flat DTO types.
 *
 * <p>The Core-to-application read semantics are owned by the shared {@link BasicResourceReader}:
 * resource-type matching, strict and independent identity-role selection, wire-member presence,
 * attribute, relationship and meta order, the synthetic input map, cardinality validation,
 * null/empty short-circuiting, direct identifier copying, wrapper occurrence orchestration,
 * identifier-meta sequencing, resource/relationship meta binding, and the member-relative
 * diagnostics. This adapter supplies only the native edges through {@link ReadResourceBackend}:
 * configured wire identifier parsing, lazy relationship-shape resolution (native target/type
 * resolution plus configured-mapper selection), configured linkage-mapper invocation, declared
 * identifier-meta conversion, and the single configured bean construction.
 *
 * <p>Identifier, attribute, relationship, and meta values are placed into a synthetic property map
 * keyed by Jackson logical property names, then the bean is constructed with a single {@link
 * JsonMapper#convertValue(Object, JavaType)} so creators, deserializers, converters, and configured
 * modules remain authoritative. The JSON:API identifier is parsed before it enters the map, so its
 * target property's configured deserializer still applies during construction. Document {@code
 * included} is never read; relationships bind from linkage only.
 *
 * <p>Read-side bindability is resolved from Jackson's effective deserialization model,
 * independently of the serialization-oriented {@link ResourceMapping}. Setter-only, creator-only,
 * and write-only properties can therefore bind normally, while a supplied member with no effective
 * deserialization target fails with {@link MappingDiagnostic#NON_DESERIALIZABLE_PROPERTY} at its
 * JSON:API wire location.
 *
 * <p>Diagnostic locations follow the shared mapping-location contract: resource-relative pointers
 * over wire names ({@code /type}, {@code /id}, {@code /lid}, {@code /attributes/<name>}, {@code
 * /relationships/<name>/data}, resource/relationship meta locations, identifier-meta locations).
 * Bean-construction failures translate their Jackson failure paths through this mapping; unmappable
 * paths carry an absent location instead of a logical property name.
 */
public final class DomainResourceBinder
    implements ReadResourceBackend<JavaType, ReadMappingProperty> {

  private final JsonMapper mapper;
  private final IdentifierConverter identifierConverter;
  private final MappingDefinitionCache cache;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;
  private final WholeMetaTarget wholeMetaTarget;
  private final FlatConstructionPaths constructionPaths;
  private final BasicResourceReader<JavaType, ReadMappingProperty> reader;

  public DomainResourceBinder(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      MappingDefinitionCache cache,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.identifierConverter = Objects.requireNonNull(identifierConverter, "identifierConverter");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.linkageMappers = Map.copyOf(Objects.requireNonNull(linkageMappers, "linkageMappers"));
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
    this.constructionPaths = new FlatConstructionPaths(mapper);
    this.reader = new BasicResourceReader<>(this);
  }

  /** Binds one resource object to the given target type. */
  public Object fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");
    Class<?> rawType = targetType.getRawClass();
    ReadResourceMapping mapping = cache.resolveRead(targetType);
    ReadResourceDefinition<ReadMappingProperty> definition = mapping.readDefinition();
    reader.requireResourceType(resource, definition, rawType);
    // Whole-meta declared-target validation for the read/write domain-mapping role.
    wholeMetaTarget.validateReadWriteTargets(mapping, rawType);
    BasicReadResult result = reader.readBasic(resource, definition, rawType);
    Map<String, ReadConstructionStart<ReadMappingProperty>> constructionStarts =
        definition.constructionStarts(result.identifierLocation(), result.localIdLocation());
    return convertBean(
        result.properties(),
        targetType,
        rawType,
        mapping,
        constructionStarts,
        result.identifierLocation(),
        result.localIdLocation());
  }

  @Override
  public Class<?> rawType(ReadProperty<ReadMappingProperty> property) {
    return RelationshipLinkageSupport.rawTypeOf(property.token());
  }

  @Override
  public @Nullable Object parseIdentifier(String wireIdentifier) {
    return identifierConverter.parse(wireIdentifier);
  }

  @Override
  public ReadRelationshipShape<JavaType> readRelationshipShape(
      ReadProperty<ReadMappingProperty> property) {
    ReadMappingProperty nativeProperty = property.token();
    return RelationshipLinkageSupport.readRelationshipShape(
        nativeProperty.type(), nativeProperty, linkageMappers, mapper.getTypeFactory());
  }

  @Override
  public @Nullable Object mapLinkage(
      ReadProperty<ReadMappingProperty> property, RelationshipData data, JavaType target) {
    return RelationshipLinkageSupport.mapLinkage(data, target, property.token(), linkageMappers);
  }

  @Override
  public @Nullable Object convertIdentifierMeta(
      ReadProperty<ReadMappingProperty> property,
      Meta meta,
      JavaType metaToken,
      int occurrenceIndex) {
    return RelationshipLinkageSupport.convertIdentifierMeta(
        meta, metaToken, mapper, property.token(), occurrenceIndex);
  }

  private Object convertBean(
      Map<String, @Nullable Object> properties,
      JavaType targetType,
      Class<?> rawType,
      ReadResourceMapping mapping,
      Map<String, ReadConstructionStart<ReadMappingProperty>> constructionStarts,
      @Nullable MappingLocation idLocation,
      @Nullable MappingLocation lidLocation) {
    try {
      return BeanConstruction.convertBean(
          mapper,
          properties,
          targetType,
          rawType,
          (failure, ignored) ->
              constructionPaths.translateConstructionPath(
                  BeanConstruction.pathNames(failure), constructionStarts),
          mapping.creatorPropertyNames());
    } catch (JsonApiMappingException e) {
      ReadMappingProperty identifierProperty = mapping.identifierProperty();
      if (identifierProperty != null
          && idLocation != null
          && BeanConstruction.isConstructionFailureForProperty(e, identifierProperty, idLocation)) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw BasicResourceReader.identifierConversionFailure(rawType, idLocation, cause);
      }
      ReadMappingProperty localIdProperty = mapping.localIdProperty();
      if (localIdProperty != null
          && lidLocation != null
          && BeanConstruction.isConstructionFailureForProperty(e, localIdProperty, lidLocation)) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw BasicResourceReader.identifierConversionFailure(rawType, lidLocation, cause);
      }
      throw e;
    }
  }
}
