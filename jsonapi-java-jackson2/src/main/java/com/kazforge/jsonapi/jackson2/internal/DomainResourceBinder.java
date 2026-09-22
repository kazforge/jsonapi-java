package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.internal.BasicReadResult;
import com.kazforge.jsonapi.mapping.internal.BasicResourceReader;
import com.kazforge.jsonapi.mapping.internal.ReadProperty;
import com.kazforge.jsonapi.mapping.internal.ReadResourceBackend;
import com.kazforge.jsonapi.mapping.internal.ReadResourceDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Binds validated {@link ResourceObject} values to annotated flat DTO types.
 *
 * <p>The basic Core-to-application read semantics are owned by the shared {@link
 * BasicResourceReader}: resource-type matching, strict and independent identity-role selection,
 * wire-member presence, attribute and relationship order, the synthetic input map, and the
 * member-relative diagnostics. This adapter supplies the native edges through {@link
 * ReadResourceBackend} (configured wire identifier parsing and configured relationship-linkage
 * conversion) and keeps whole-object meta and relationship-meta binding plus the single configured
 * bean construction.
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
public final class DomainResourceBinder implements ReadResourceBackend<ReadMappingProperty> {

  private final JsonMapper mapper;
  private final IdentifierConverter identifierConverter;
  private final MappingDefinitionCache cache;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;
  private final WholeMetaTarget wholeMetaTarget;
  private final FlatConstructionPaths constructionPaths;
  private final BasicResourceReader<ReadMappingProperty> reader;

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
    Map<String, @Nullable Object> properties = new LinkedHashMap<>(result.properties());
    bindResourceMeta(resource, mapping, properties, rawType);
    bindRelationshipMeta(resource, mapping, properties, rawType);
    return convertBean(
        properties,
        targetType,
        rawType,
        mapping,
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
  public @Nullable Object convertRelationship(
      ReadProperty<ReadMappingProperty> property, RelationshipData data) {
    ReadMappingProperty nativeProperty = property.token();
    JavaType propertyType = nativeProperty.type();
    JavaType mappingType =
        MappingTypeSupport.targetMappingType(propertyType, mapper.getTypeFactory());
    RelationshipLinkageMapper linkageMapper =
        RelationshipLinkageSupport.selectLinkageMapper(
            propertyType, nativeProperty, linkageMappers);
    return RelationshipLinkageSupport.convertLinkage(
        nativeProperty, data, linkageMapper, mappingType, mapper);
  }

  /**
   * Binds the resource-side {@code meta} members under the mapped resource-meta property's
   * configured Jackson external name when the resource carries meta. Absent meta leaves the
   * property absent.
   */
  private void bindResourceMeta(
      ResourceObject resource,
      ReadResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    ReadMappingProperty resourceMetaProperty = mapping.resourceMeta();
    if (resourceMetaProperty == null || resource.meta() == null) {
      return;
    }
    requireDeserializable(
        resourceMetaProperty, RelationshipMetaSupport.resourceMetaLocation(), rawType);
    properties.put(resourceMetaProperty.externalName(), resource.meta().members());
  }

  /**
   * Binds relationship {@code meta} members under each mapped relationship-meta property's
   * configured Jackson external name when the referenced relationship is present and carries meta.
   * Absent relationship or absent meta leaves the property absent. A valid meta-only relationship
   * representation binds its meta here (read side); PATCH additionally requires {@code data}.
   */
  private void bindRelationshipMeta(
      ResourceObject resource,
      ReadResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    if (mapping.relationshipMetaProperties().isEmpty()) {
      return;
    }
    Relationships relationships = resource.relationships();
    if (relationships == null) {
      return;
    }
    for (ReadMappingProperty property : mapping.relationshipMetaProperties()) {
      Relationship relationship = relationships.relationships().get(property.jsonapiName());
      if (relationship == null || relationship.meta() == null) {
        continue;
      }
      requireDeserializable(
          property,
          RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()),
          rawType);
      properties.put(property.externalName(), relationship.meta().members());
    }
  }

  private static void requireDeserializable(
      ReadMappingProperty property, MappingLocation location, Class<?> rawType) {
    if (property.deserializable()) {
      return;
    }
    throw new JsonApiMappingException(
        MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY,
        rawType,
        location,
        "Supplied JSON:API member at '"
            + location
            + "' targets property '"
            + property.logicalName()
            + "' without an effective deserialization target on "
            + rawType.getName());
  }

  private Object convertBean(
      Map<String, @Nullable Object> properties,
      JavaType targetType,
      Class<?> rawType,
      ReadResourceMapping mapping,
      @Nullable MappingLocation idLocation,
      @Nullable MappingLocation lidLocation) {
    Map<String, MappingConstructionStart> startsByJacksonName =
        mapping.constructionStartsByJacksonName(idLocation, lidLocation);
    try {
      return BeanConstruction.convertBean(
          mapper,
          properties,
          targetType,
          rawType,
          (failure, ignored) ->
              constructionPaths.translateConstructionPath(
                  BeanConstruction.pathNames(failure), startsByJacksonName),
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
