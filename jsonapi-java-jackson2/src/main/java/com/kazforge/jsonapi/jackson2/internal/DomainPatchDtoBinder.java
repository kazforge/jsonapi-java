package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.internal.TypedPatchBackend;
import com.kazforge.jsonapi.mapping.internal.TypedPatchBinder;
import com.kazforge.jsonapi.mapping.internal.TypedPatchDefinition;
import com.kazforge.jsonapi.mapping.internal.TypedPatchProperty;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Binds a validated single-resource update directly into an application-owned annotated PATCH DTO.
 *
 * <p>The neutral typed PATCH orchestration is owned by the shared {@link TypedPatchBinder}:
 * resource-type match, complete typed declaration preflight, required identity, attribute,
 * relationship, resource-meta, and relationship-meta phases in contract order, strict supplied
 * unknown-member handling, meta/data gating, and synthetic {@code PresenceMarker} assembly. This
 * adapter supplies only native edges through {@link TypedPatchBackend}: declared
 * relationship-linkage identifier-meta validation, wire-identifier parsing, typed relationship
 * linkage conversion, final bean construction, construction-path translation, and
 * identifier-construction failure reclassification. Recursive structured attributes use the shared
 * structured engine through {@link StructuredValueBinder}.
 *
 * <p>Supplied atomic members retain their JSON-compatible wire values in a synthetic property map
 * as an internal {@link com.kazforge.jsonapi.mapping.internal.PresenceMarker}; the marker
 * deserializer performs the sole inner-type conversion while the bean is constructed with a single
 * {@link JsonMapper#convertValue(Object, JavaType)}. Creators, deserializers, converters, and
 * configured modules therefore remain authoritative. Omitted members bind to {@code
 * PatchPresence.omitted()}. Document {@code included} is never read.
 */
public final class DomainPatchDtoBinder implements TypedPatchBackend<MappingProperty, JavaType> {

  private static final MappingLocation ID_LOCATION = MappingLocation.of("id");

  private final JsonMapper mapper;
  private final MappingDefinitionCache cache;
  private final PatchMemberConverter converter;
  private final StructuredValueBinder structuredBinder;
  private final WholeMetaTarget wholeMetaTarget;
  private final TypedPatchBinder<MappingProperty, JavaType> binder;

  public DomainPatchDtoBinder(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      MappingDefinitionCache cache,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.converter = new PatchMemberConverter(mapper, identifierConverter, linkageMappers);
    this.structuredBinder = new StructuredValueBinder(mapper);
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
    this.binder = new TypedPatchBinder<>(this, structuredBinder::typedMemberValue);
  }

  /** Binds one resource object into a PATCH DTO instance of {@code targetType}. */
  public Object fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");
    Class<?> rawType = targetType.getRawClass();
    ResourceMapping mapping = cache.resolve(targetType);
    return binder.bind(resource, toDefinition(mapping), targetType, rawType);
  }

  private TypedPatchDefinition<MappingProperty, JavaType> toDefinition(ResourceMapping mapping) {
    return new TypedPatchDefinition<>(
        mapping.resourceType(),
        typedOrNull(mapping.identifierProperty(), false),
        typedProperties(mapping.attributes(), false),
        typedProperties(mapping.relationships(), false),
        typedOrNull(mapping.resourceMeta(), true),
        typedProperties(mapping.relationshipMetaProperties(), true));
  }

  private List<TypedPatchProperty<MappingProperty, JavaType>> typedProperties(
      List<MappingProperty> properties, boolean meta) {
    List<TypedPatchProperty<MappingProperty, JavaType>> typed = new ArrayList<>(properties.size());
    for (MappingProperty property : properties) {
      typed.add(typedProperty(property, meta));
    }
    return typed;
  }

  private @Nullable TypedPatchProperty<MappingProperty, JavaType> typedOrNull(
      @Nullable MappingProperty property, boolean meta) {
    return property == null ? null : typedProperty(property, meta);
  }

  private TypedPatchProperty<MappingProperty, JavaType> typedProperty(
      MappingProperty property, boolean meta) {
    JavaType declared = property.accessor().getType();
    return new TypedPatchProperty<>(
        property,
        property.metadata(),
        declared,
        isPatchPresenceType(declared),
        WrapperCustomization.has(
            mapper, declared, property.accessor(), property.definition().getMutator()),
        meta && validTypedPatchTarget(property.definition().getPrimaryType()));
  }

  private boolean validTypedPatchTarget(JavaType declared) {
    if (!isPatchPresenceType(declared)) {
      return false;
    }
    JavaType inner = declared.containedType(0);
    JavaType effective = isOptional(inner) ? inner.containedType(0) : inner;
    if (isOptional(effective)) {
      return false;
    }
    return !wholeMetaTarget.invalidReadWriteTarget(effective);
  }

  private static boolean isPatchPresenceType(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1;
  }

  private static boolean isOptional(JavaType type) {
    return type.getRawClass() == java.util.Optional.class && type.containedTypeCount() == 1;
  }

  // ============================== TypedPatchBackend ==============================

  @Override
  public void validateRelationshipLinkageMeta(
      TypedPatchDefinition<MappingProperty, JavaType> definition, Class<?> rawType) {
    wholeMetaTarget.validateRelationshipLinkageMeta(
        definition.relationships().stream().map(TypedPatchProperty::token).toList(), rawType);
  }

  @Override
  public Object parseIdentity(
      String wireIdentifier,
      TypedPatchProperty<MappingProperty, JavaType> identifier,
      Class<?> rawType) {
    return converter.parseIdentity(wireIdentifier, rawType);
  }

  @Override
  public @Nullable Object convertRelationship(
      TypedPatchProperty<MappingProperty, JavaType> property,
      RelationshipData data,
      Class<?> rawType) {
    JavaType inner = property.declaredType().containedType(0);
    return converter.convertRelationshipForPatchDto(property.token(), data, inner);
  }

  @Override
  public Object construct(
      JavaType targetType,
      Map<String, @Nullable Object> properties,
      Class<?> rawType,
      @Nullable TypedPatchProperty<MappingProperty, JavaType> identifier) {
    ResourceMapping mapping = cache.resolve(targetType);
    Map<String, MappingConstructionStart> startsByJacksonName =
        mapping.constructionStartsByJacksonName(ID_LOCATION, null);
    MappingProperty identifierProperty = identifier == null ? null : identifier.token();
    try {
      return BeanConstruction.convertBean(
          mapper,
          properties,
          targetType,
          rawType,
          (failure, ignored) ->
              structuredBinder.translateConstructionPath(
                  BeanConstruction.pathNames(failure), startsByJacksonName),
          creatorPropertyNames(targetType));
    } catch (JsonApiMappingException e) {
      if (isIdentifierConstructionFailure(e, identifierProperty)) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new JsonApiMappingException(
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
            rawType,
            ID_LOCATION,
            "Failed to convert the wire identifier at '"
                + ID_LOCATION
                + "' for "
                + rawType.getName(),
            cause);
      }
      throw e;
    }
  }

  private static boolean isIdentifierConstructionFailure(
      JsonApiMappingException failure, @Nullable MappingProperty identifierProperty) {
    if (identifierProperty == null) {
      return false;
    }
    return BeanConstruction.isConstructionFailureForProperty(
        failure, identifierProperty, ID_LOCATION);
  }

  private Set<String> creatorPropertyNames(JavaType targetType) {
    Set<String> names = new HashSet<>();
    BeanDescription serializationDescription =
        mapper
            .getSerializationConfig()
            .getClassIntrospector()
            .forSerialization(
                mapper.getSerializationConfig(), targetType, mapper.getSerializationConfig());
    for (BeanPropertyDefinition definition : serializationDescription.findProperties()) {
      if (definition.hasConstructorParameter()) {
        names.add(definition.getName());
      }
    }
    BeanDescription deserializationDescription =
        mapper
            .getDeserializationConfig()
            .getClassIntrospector()
            .forDeserialization(
                mapper.getDeserializationConfig(), targetType, mapper.getDeserializationConfig());
    for (BeanPropertyDefinition definition : deserializationDescription.findProperties()) {
      if (definition.hasConstructorParameter()) {
        names.add(definition.getName());
      }
    }
    return Set.copyOf(names);
  }
}
