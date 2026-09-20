package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.jackson.internal.mapping.PropertyRole;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.internal.BindingDefinition;
import com.kazforge.jsonapi.mapping.internal.BindingPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.DomainBindingBackend;
import com.kazforge.jsonapi.mapping.internal.MappingRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** KAZ-137 bridge from the existing jackson2 deserialization model to the neutral binding PoC. */
public final class Jackson2PrototypeBindingBackend
    implements DomainBindingBackend<JavaType, ReadMappingProperty> {

  private final JsonMapper mapper;
  private final MappingDefinitionCache cache;
  private final IdentifierConverter identifierConverter;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;

  public Jackson2PrototypeBindingBackend(JsonMapper mapper) {
    this(mapper, IdentifierConverter.defaults(), Map.of());
  }

  public Jackson2PrototypeBindingBackend(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.mapper = java.util.Objects.requireNonNull(mapper, "mapper");
    this.cache = new MappingDefinitionCache(mapper);
    this.identifierConverter =
        java.util.Objects.requireNonNull(identifierConverter, "identifierConverter");
    this.linkageMappers = Map.copyOf(linkageMappers);
  }

  @Override
  public JavaType constructType(Class<?> rawType) {
    return cache.constructType(rawType);
  }

  @Override
  public Class<?> rawClass(JavaType type) {
    return type.getRawClass();
  }

  @Override
  public BindingDefinition<JavaType, ReadMappingProperty> bindingFor(JavaType type) {
    ReadResourceMapping mapping = cache.resolveRead(type);
    return new BindingDefinition<>(
        mapping.resourceType(),
        type,
        translateNullable(mapping.identifierProperty()),
        translateNullable(mapping.localIdProperty()),
        translateAll(mapping.attributes()),
        translateAll(mapping.relationships()));
  }

  @Override
  public @Nullable Object parseIdentifier(String wireIdentifier) {
    return identifierConverter.parse(wireIdentifier);
  }

  @Override
  public @Nullable Object convertRelationship(
      RelationshipData data, BindingPropertyDefinition<JavaType, ReadMappingProperty> property) {
    ReadMappingProperty nativeProperty = property.handle();
    JavaType propertyType = nativeProperty.type();
    JavaType mappingType =
        MappingTypeSupport.targetMappingType(propertyType, mapper.getTypeFactory());
    RelationshipLinkageMapper linkageMapper =
        RelationshipLinkageSupport.selectLinkageMapper(
            propertyType, nativeProperty, linkageMappers);
    return RelationshipLinkageSupport.convertLinkage(
        nativeProperty, data, linkageMapper, mappingType, mapper);
  }

  @Override
  public Object construct(Map<String, @Nullable Object> properties, JavaType targetType) {
    ReadResourceMapping mapping = cache.resolveRead(targetType);
    return BeanConstruction.convertBean(
        mapper,
        properties,
        targetType,
        targetType.getRawClass(),
        null,
        mapping.creatorPropertyNames());
  }

  private @Nullable BindingPropertyDefinition<JavaType, ReadMappingProperty> translateNullable(
      @Nullable ReadMappingProperty property) {
    return property == null ? null : translate(property);
  }

  private BindingPropertyDefinition<JavaType, ReadMappingProperty> translate(
      ReadMappingProperty property) {
    MappingRole mappingRole = role(property.role());
    return new BindingPropertyDefinition<>(
        property,
        property.logicalName(),
        property.jacksonName(),
        jsonapiName(mappingRole, property.jsonapiName()),
        mappingRole,
        property.type(),
        property.deserializable());
  }

  private static String jsonapiName(MappingRole role, String backendResolvedName) {
    return switch (role) {
      case ID -> "id";
      case LOCAL_ID -> "lid";
      case ATTRIBUTE, RELATIONSHIP -> backendResolvedName;
    };
  }

  private List<BindingPropertyDefinition<JavaType, ReadMappingProperty>> translateAll(
      List<ReadMappingProperty> properties) {
    List<BindingPropertyDefinition<JavaType, ReadMappingProperty>> result =
        new ArrayList<>(properties.size());
    for (ReadMappingProperty property : properties) {
      result.add(translate(property));
    }
    return List.copyOf(result);
  }

  private static MappingRole role(PropertyRole role) {
    return switch (role) {
      case ID -> MappingRole.ID;
      case LOCAL_ID -> MappingRole.LOCAL_ID;
      case ATTRIBUTE -> MappingRole.ATTRIBUTE;
      case RELATIONSHIP -> MappingRole.RELATIONSHIP;
      case RESOURCE_META, RELATIONSHIP_META ->
          throw new IllegalArgumentException("Meta is outside this PoC slice: " + role);
    };
  }
}
