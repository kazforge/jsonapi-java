package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Shared JSON:API core-resource to application-value binding orchestration.
 *
 * <p>The shared layer owns JSON:API member presence and role selection. The concrete JSON-library
 * backend owns deserialization introspection, type/container conversion and object construction.
 *
 * <p>This KAZ-137 slice intentionally excludes meta and custom RelationshipLinkage handling from
 * the neutral layer; those remain backend conversion concerns to evaluate separately.
 */
public final class GenericDomainResourceBinder<T, P> {

  private final DomainBindingBackend<T, P> backend;

  public GenericDomainResourceBinder(DomainBindingBackend<T, P> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public <R> R fromResource(ResourceObject resource, Class<R> targetClass) {
    Objects.requireNonNull(targetClass, "targetClass");
    Object bound = fromResource(resource, backend.constructType(targetClass));
    return targetClass.cast(bound);
  }

  public Object fromResource(ResourceObject resource, T targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");

    BindingDefinition<T, P> mapping = backend.bindingFor(targetType);
    Class<?> rawType = backend.rawClass(targetType);
    if (!mapping.resourceType().equals(resource.type())) {
      throw new JsonApiMappingException(
          MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
          rawType,
          MappingLocation.of("type"),
          "Resource object type '"
              + resource.type()
              + "' does not match expected type '"
              + mapping.resourceType()
              + "'");
    }

    Map<String, @Nullable Object> properties = new LinkedHashMap<>();
    bindIdentity(resource, mapping.idProperty(), resource.id(), "id", rawType, properties);
    bindIdentity(resource, mapping.localIdProperty(), resource.lid(), "lid", rawType, properties);
    bindAttributes(resource, mapping, rawType, properties);
    bindRelationships(resource, mapping, rawType, properties);
    return backend.construct(properties, targetType);
  }

  private void bindIdentity(
      ResourceObject resource,
      @Nullable BindingPropertyDefinition<T, P> property,
      @Nullable String wireValue,
      String memberName,
      Class<?> rawType,
      Map<String, @Nullable Object> properties) {
    if (property == null || wireValue == null) {
      return;
    }
    MappingLocation location = MappingLocation.of(memberName);
    requireBindable(property, location, rawType);
    Object parsed;
    try {
      parsed = backend.parseIdentifier(wireValue);
    } catch (RuntimeException ex) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          location,
          "Failed to convert wire identifier at '" + location + "'",
          ex);
    }
    if (parsed == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          location,
          "Identifier conversion returned null at '" + location + "'");
    }
    properties.put(property.externalName(), parsed);
  }

  private void bindAttributes(
      ResourceObject resource,
      BindingDefinition<T, P> mapping,
      Class<?> rawType,
      Map<String, @Nullable Object> properties) {
    Attributes attributes = resource.attributes();
    if (attributes == null) {
      return;
    }
    Map<String, @Nullable Object> members = attributes.attributes();
    for (BindingPropertyDefinition<T, P> property : mapping.attributes()) {
      if (!members.containsKey(property.jsonapiName())) {
        continue;
      }
      MappingLocation location = MappingLocation.of("attributes", property.jsonapiName());
      requireBindable(property, location, rawType);
      properties.put(property.externalName(), members.get(property.jsonapiName()));
    }
  }

  private void bindRelationships(
      ResourceObject resource,
      BindingDefinition<T, P> mapping,
      Class<?> rawType,
      Map<String, @Nullable Object> properties) {
    Relationships relationships = resource.relationships();
    if (relationships == null) {
      return;
    }
    for (BindingPropertyDefinition<T, P> property : mapping.relationships()) {
      Relationship relationship = relationships.relationships().get(property.jsonapiName());
      if (relationship == null) {
        continue;
      }
      RelationshipData data = relationship.data();
      if (data == null) {
        continue;
      }
      MappingLocation location =
          MappingLocation.of("relationships", property.jsonapiName(), "data");
      requireBindable(property, location, rawType);
      properties.put(property.externalName(), backend.convertRelationship(data, property));
    }
  }

  private static <T, P> void requireBindable(
      BindingPropertyDefinition<T, P> property, MappingLocation location, Class<?> rawType) {
    if (property.bindable()) {
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
}
