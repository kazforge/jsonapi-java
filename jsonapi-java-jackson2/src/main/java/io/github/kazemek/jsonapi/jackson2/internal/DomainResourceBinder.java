package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.model.Attributes;
import io.github.kazemek.jsonapi.core.model.Relationship;
import io.github.kazemek.jsonapi.core.model.RelationshipData;
import io.github.kazemek.jsonapi.core.model.Relationships;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.internal.mapping.ResourceTypeMatch;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson2.RelationshipLinkageMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Binds validated {@link ResourceObject} values to annotated flat DTO types.
 *
 * <p>Identifier, attribute, and relationship values are placed into a synthetic property map keyed
 * by Jackson logical property names, then the bean is constructed with a single {@link
 * JsonMapper#convertValue(Object, JavaType)} so creators, deserializers, converters, and configured
 * modules remain authoritative (ADR-004). The JSON:API identifier is parsed before it enters the
 * map, so its target property's configured deserializer still applies during construction. Document
 * {@code included} is never read; relationships bind from linkage only (ADR-011).
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
public final class DomainResourceBinder {

  private static final MappingLocation ID_LOCATION = MappingLocation.of("id");
  private static final MappingLocation LID_LOCATION = MappingLocation.of("lid");

  private final JsonMapper mapper;
  private final IdentifierConverter identifierConverter;
  private final MappingDefinitionCache cache;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;
  private final WholeMetaTarget wholeMetaTarget;
  private final FlatConstructionPaths constructionPaths;

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
  }

  /** Binds one resource object to the given target type. */
  public Object fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");
    Class<?> rawType = targetType.getRawClass();
    ReadResourceMapping mapping = cache.resolveRead(targetType);
    ResourceTypeMatch.requireMatching(mapping.resourceType(), resource, rawType);
    // Whole-meta declared-target validation for the read/write domain-mapping role (ADR-015).
    wholeMetaTarget.validateReadWriteTargets(mapping, rawType);
    Map<String, @Nullable Object> properties = new LinkedHashMap<>();
    // Strict role mapping: wire id binds only to the id role, wire lid only to the local-id role.
    // Neither member ever falls back into the other role's property.
    MappingLocation idLocation = null;
    ReadMappingProperty identifierProperty = mapping.identifierProperty();
    if (identifierProperty != null && resource.hasId()) {
      idLocation = ID_LOCATION;
      requireDeserializable(identifierProperty, idLocation, rawType);
      bindIdentifierValue(
          Objects.requireNonNull(resource.id()), ID_LOCATION, identifierProperty, properties);
    }
    MappingLocation lidLocation = null;
    ReadMappingProperty localIdProperty = mapping.localIdProperty();
    if (localIdProperty != null && resource.hasLid()) {
      lidLocation = LID_LOCATION;
      requireDeserializable(localIdProperty, lidLocation, rawType);
      bindIdentifierValue(
          Objects.requireNonNull(resource.lid()), LID_LOCATION, localIdProperty, properties);
    }
    bindAttributes(resource, mapping, properties, rawType);
    bindRelationships(resource, mapping, properties, rawType);
    bindResourceMeta(resource, mapping, properties, rawType);
    bindRelationshipMeta(resource, mapping, properties, rawType);
    return convertBean(properties, targetType, rawType, mapping, idLocation, lidLocation);
  }

  private void bindIdentifierValue(
      String wireIdentifier,
      MappingLocation identifierLocation,
      ReadMappingProperty identifierProperty,
      Map<String, @Nullable Object> properties) {
    Object parsed;
    try {
      parsed = identifierConverter.parse(wireIdentifier);
    } catch (RuntimeException e) {
      throw identifierConversionFailed(rawTypeOf(identifierProperty), identifierLocation, e);
    }
    if (parsed == null) {
      throw identifierConversionFailed(rawTypeOf(identifierProperty), identifierLocation, null);
    }
    // Keep the parsed JSON:API intermediate in the synthetic property map. The final bean
    // construction then applies the target property's fully contextualized Jackson deserializer
    // exactly once, rather than converting the detached identifier as a root value first.
    properties.put(identifierProperty.jacksonName(), parsed);
  }

  private JsonApiMappingException identifierConversionFailed(
      Class<?> rawType, MappingLocation identifierLocation, @Nullable Throwable cause) {
    String message =
        cause == null
            ? "Identifier converter returned null for the wire identifier at '"
                + identifierLocation
                + "'"
            : "Failed to convert the wire identifier at '"
                + identifierLocation
                + "' for "
                + rawType.getName();
    return cause == null
        ? new JsonApiMappingException(
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED, rawType, identifierLocation, message)
        : new JsonApiMappingException(
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
            rawType,
            identifierLocation,
            message,
            cause);
  }

  private static Class<?> rawTypeOf(MappingPropertyView property) {
    return RelationshipLinkageSupport.rawTypeOf(property);
  }

  private void bindAttributes(
      ResourceObject resource,
      ReadResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    if (mapping.attributes().isEmpty()) {
      return;
    }
    Attributes attributes = resource.attributes();
    if (attributes == null) {
      return;
    }
    Map<String, @Nullable Object> members = attributes.attributes();
    for (ReadMappingProperty property : mapping.attributes()) {
      if (!members.containsKey(property.jsonapiName())) {
        continue;
      }
      requireDeserializable(
          property, MappingLocation.of("attributes", property.jsonapiName()), rawType);
      properties.put(property.jacksonName(), members.get(property.jsonapiName()));
    }
  }

  private void bindRelationships(
      ResourceObject resource,
      ReadResourceMapping mapping,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    if (mapping.relationships().isEmpty()) {
      return;
    }
    Relationships relationships = resource.relationships();
    if (relationships == null) {
      return;
    }
    for (ReadMappingProperty property : mapping.relationships()) {
      RelationshipData data = relationshipData(relationships, property);
      if (data == null) {
        continue;
      }
      requireDeserializable(
          property, RelationshipLinkageSupport.relationshipLocation(property), rawType);
      bindRelationship(properties, property, data);
    }
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
    properties.put(resourceMetaProperty.jacksonName(), resource.meta().members());
  }

  /**
   * Binds relationship {@code meta} members under each mapped relationship-meta property's
   * configured Jackson external name when the referenced relationship is present and carries meta.
   * Absent relationship or absent meta leaves the property absent. A valid meta-only relationship
   * representation binds its meta here (read side); PATCH additionally requires {@code data}
   * (ADR-015).
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
      properties.put(property.jacksonName(), relationship.meta().members());
    }
  }

  private static @Nullable RelationshipData relationshipData(
      Relationships relationships, MappingPropertyView property) {
    Relationship relationship = relationships.relationships().get(property.jsonapiName());
    if (relationship == null) {
      return null;
    }
    return relationship.data();
  }

  private void bindRelationship(
      Map<String, @Nullable Object> properties,
      ReadMappingProperty property,
      RelationshipData data) {
    JavaType propertyType = property.type();
    JavaType mappingType =
        RelationshipLinkageSupport.targetMappingType(propertyType, mapper.getTypeFactory());
    RelationshipLinkageMapper linkageMapper =
        RelationshipLinkageSupport.selectLinkageMapper(propertyType, property, linkageMappers);
    properties.put(
        property.jacksonName(),
        RelationshipLinkageSupport.convertLinkage(
            property, data, linkageMapper, mappingType, mapper));
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
            + "' without an effective Jackson deserialization target on "
            + rawType.getName());
  }

  private Object convertBean(
      Map<String, @Nullable Object> properties,
      JavaType targetType,
      Class<?> rawType,
      ReadResourceMapping mapping,
      @Nullable MappingLocation idLocation,
      @Nullable MappingLocation lidLocation) {
    Map<String, FlatConstructionPaths.ConstructionStart> startsByJacksonName =
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
        throw identifierConversionFailed(rawType, idLocation, cause);
      }
      ReadMappingProperty localIdProperty = mapping.localIdProperty();
      if (localIdProperty != null
          && lidLocation != null
          && BeanConstruction.isConstructionFailureForProperty(e, localIdProperty, lidLocation)) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw identifierConversionFailed(rawType, lidLocation, cause);
      }
      throw e;
    }
  }
}
