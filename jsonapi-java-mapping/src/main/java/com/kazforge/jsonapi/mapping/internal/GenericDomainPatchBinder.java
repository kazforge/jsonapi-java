package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson.patch.PatchChange;
import com.kazforge.jsonapi.jackson.patch.PatchCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Shared low-level PATCH change orchestration for a representative KAZ-137 slice.
 *
 * <p>Member presence, wire-name selection and change ordering are mapping-domain responsibilities.
 * Backend-specific value/linkage conversion remains behind {@link DomainPatchBackend}.
 *
 * <p>Meta and recursively structured values intentionally remain outside this prototype slice.
 */
public final class GenericDomainPatchBinder<T, P> {

  private static final MappingLocation ID_LOCATION = MappingLocation.of("id");

  private final DomainPatchBackend<T, P> backend;

  public GenericDomainPatchBinder(DomainPatchBackend<T, P> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public PatchCommand<?> fromResource(ResourceObject resource, T targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");

    MappingDefinition<T, P> mapping = backend.mappingFor(targetType);
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

    MappingPropertyDefinition<T, P> idProperty = mapping.idProperty();
    if (idProperty == null || resource.id() == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          ID_LOCATION,
          "Resource update identity requires a non-null id at '" + ID_LOCATION + "'");
    }

    Object identity =
        backend.convertPatchIdentity(Objects.requireNonNull(resource.id()), mapping, idProperty);
    List<PatchChange> changes = new ArrayList<>();
    bindAttributes(resource, mapping, changes);
    bindRelationships(resource, mapping, changes);
    return new PatchCommand(rawType, identity, changes);
  }

  private void bindAttributes(
      ResourceObject resource, MappingDefinition<T, P> mapping, List<PatchChange> changes) {
    Attributes attributes = resource.attributes();
    if (attributes == null || mapping.attributes().isEmpty()) {
      return;
    }
    Map<String, MappingPropertyDefinition<T, P>> byName = byName(mapping.attributes());
    for (Map.Entry<String, @Nullable Object> entry : attributes.attributes().entrySet()) {
      MappingPropertyDefinition<T, P> property = byName.get(entry.getKey());
      if (property == null) {
        continue;
      }
      Object value = backend.convertPatchAttribute(entry.getValue(), mapping, property);
      changes.add(
          new PatchChange.AttributeChange(property.jsonapiName(), property.logicalName(), value));
    }
  }

  private void bindRelationships(
      ResourceObject resource, MappingDefinition<T, P> mapping, List<PatchChange> changes) {
    Relationships relationships = resource.relationships();
    if (relationships == null || mapping.relationships().isEmpty()) {
      return;
    }
    Map<String, MappingPropertyDefinition<T, P>> byName = byName(mapping.relationships());
    for (Map.Entry<String, Relationship> entry : relationships.relationships().entrySet()) {
      MappingPropertyDefinition<T, P> property = byName.get(entry.getKey());
      if (property == null) {
        continue;
      }
      RelationshipData data = entry.getValue().data();
      if (data == null) {
        continue;
      }
      Object value = backend.convertPatchRelationship(data, mapping, property);
      changes.add(
          new PatchChange.RelationshipChange(
              property.jsonapiName(), property.logicalName(), value));
    }
  }

  private static <T, P> Map<String, MappingPropertyDefinition<T, P>> byName(
      List<MappingPropertyDefinition<T, P>> properties) {
    Map<String, MappingPropertyDefinition<T, P>> result = new java.util.LinkedHashMap<>();
    for (MappingPropertyDefinition<T, P> property : properties) {
      result.put(property.jsonapiName(), property);
    }
    return result;
  }
}
