package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.jackson.internal.mapping.PropertyRole;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.internal.DomainMappingBackend;
import com.kazforge.jsonapi.mapping.internal.MappingDefinition;
import com.kazforge.jsonapi.mapping.internal.MappingPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.MappingRole;
import com.kazforge.jsonapi.mapping.internal.MappingValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * KAZ-137 bridge from the existing jackson2 mapping metadata to the neutral mapping-domain PoC.
 */
public final class Jackson2PrototypeMappingBackend
    implements DomainMappingBackend<JavaType, MappingProperty> {

  private final MappingDefinitionCache cache;
  private final PropertyScopedValueConverter propertyScoped;
  private final IdentifierConverter identifierConverter;

  public Jackson2PrototypeMappingBackend(JsonMapper mapper) {
    this(mapper, IdentifierConverter.defaults());
  }

  public Jackson2PrototypeMappingBackend(JsonMapper mapper, IdentifierConverter identifierConverter) {
    this.cache = new MappingDefinitionCache(mapper);
    this.propertyScoped = new PropertyScopedValueConverter(mapper);
    this.identifierConverter = identifierConverter;
  }

  @Override
  public JavaType inferredType(Object domain) {
    return cache.constructType(domain.getClass());
  }

  @Override
  public JavaType effectiveType(Object domain, JavaType declaredType) {
    if (declaredType.getRawClass() == domain.getClass()) {
      return declaredType;
    }
    return cache.specializeType(declaredType, domain.getClass());
  }

  @Override
  public Class<?> rawClass(JavaType type) {
    return type.getRawClass();
  }

  @Override
  public MappingDefinition<JavaType, MappingProperty> mappingFor(JavaType type) {
    ResourceMapping mapping = cache.resolve(type);
    return new MappingDefinition<>(
        mapping.resourceType(),
        type,
        translateNullable(mapping.identifierProperty()),
        translateNullable(mapping.localIdProperty()),
        translateAll(mapping.attributes()),
        translateAll(mapping.relationships()));
  }

  @Override
  public @Nullable Object read(
      Object domain, MappingPropertyDefinition<JavaType, MappingProperty> property) {
    try {
      return property.handle().accessor().getValue(domain);
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "Failed to read mapped property '" + property.logicalName() + "'", ex);
    }
  }

  @Override
  public MappingValue convertAttribute(
      Object domain,
      MappingDefinition<JavaType, MappingProperty> mapping,
      MappingPropertyDefinition<JavaType, MappingProperty> property) {
    Object raw = read(domain, property);
    PropertyScopedValueConverter.SerializationResult result =
        propertyScoped.serialize(
            mapping.domainType(),
            property.handle().definition().getFullName().getSimpleName(),
            domain,
            raw,
            unwrapOptional(raw));
    return result.emitted() ? MappingValue.emitted(result.value()) : MappingValue.omitted();
  }

  @Override
  public @Nullable String convertIdentifier(@Nullable Object value) {
    Object unwrapped = unwrapOptional(value);
    return unwrapped == null ? null : identifierConverter.convert(unwrapped);
  }

  @Override
  public JavaType relationshipTargetType(
      MappingPropertyDefinition<JavaType, MappingProperty> property) {
    JavaType type = MappingTypeSupport.unwrapOptionalType(property.declaredType());
    if (MappingTypeSupport.isToManyType(type)) {
      JavaType content = MappingTypeSupport.resolveContentType(type);
      if (content == null) {
        throw new IllegalArgumentException(
            "Cannot resolve relationship content type: " + property.logicalName());
      }
      type = MappingTypeSupport.unwrapOptionalType(content);
    }
    JavaType linkage = MappingTypeSupport.linkageJavaType(type);
    return linkage == null ? type : MappingTypeSupport.linkageTargetType(linkage);
  }

  @Override
  public List<Object> relationshipValues(
      @Nullable Object rawValue,
      MappingPropertyDefinition<JavaType, MappingProperty> property) {
    Object value = unwrapOptional(rawValue);
    if (property.toMany()) {
      if (value == null) {
        return List.of();
      }
      List<Object> result = new ArrayList<>();
      for (Object item : DomainResourceWriter.convertToCollection(value)) {
        Object unwrapped = unwrapOptional(item);
        if (unwrapped != null) {
          result.add(unwrapped);
        }
      }
      return List.copyOf(result);
    }
    return value == null ? List.of() : List.of(value);
  }

  private @Nullable MappingPropertyDefinition<JavaType, MappingProperty> translateNullable(
      @Nullable MappingProperty property) {
    return property == null ? null : translate(property);
  }

  private MappingPropertyDefinition<JavaType, MappingProperty> translate(
      MappingProperty property) {
    JavaType type = property.accessor().getType();
    return new MappingPropertyDefinition<>(
        property,
        property.logicalName(),
        property.jacksonName(),
        property.jsonapiName(),
        role(property.role()),
        type,
        MappingTypeSupport.isToManyType(MappingTypeSupport.unwrapOptionalType(type)));
  }

  private List<MappingPropertyDefinition<JavaType, MappingProperty>> translateAll(
      List<MappingProperty> properties) {
    List<MappingPropertyDefinition<JavaType, MappingProperty>> result =
        new ArrayList<>(properties.size());
    for (MappingProperty property : properties) {
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

  private static @Nullable Object unwrapOptional(@Nullable Object value) {
    return value instanceof Optional<?> optional ? optional.orElse(null) : value;
  }
}
