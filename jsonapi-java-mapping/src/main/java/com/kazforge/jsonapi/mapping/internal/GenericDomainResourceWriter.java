package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Shared JSON:API application/domain-to-core resource mapping engine.
 *
 * <p>The implementation owns JSON:API structure and relationship semantics. Property discovery,
 * naming, access, type mechanics, and configured scalar conversion remain backend responsibilities.
 *
 * <p>This is a deliberately scoped KAZ-137 prototype. Meta, decoration, RelationshipLinkage,
 * structured values and read/PATCH binding remain outside this first vertical slice.
 */
public final class GenericDomainResourceWriter<T, P> {

  private final DomainMappingBackend<T, P> backend;
  private final GenericCompoundInclusionEngine<T> inclusionEngine;

  public GenericDomainResourceWriter(DomainMappingBackend<T, P> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.inclusionEngine = new GenericCompoundInclusionEngine<>(new InclusionBackend());
  }

  public T inferredType(Object domain) {
    return backend.inferredType(Objects.requireNonNull(domain, "domain"));
  }

  public ResourceObject toResource(Object domain) {
    return toResource(domain, inferredType(domain));
  }

  public ResourceObject toResource(Object domain, T declaredType) {
    return toResource(domain, declaredType, null);
  }

  public ResourceObject toResource(
      Object domain, T declaredType, @Nullable MappingRepresentation representation) {
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(declaredType, "declaredType");

    T effectiveType = backend.effectiveType(domain, declaredType);
    MappingDefinition<T, P> mapping = backend.mappingFor(effectiveType);
    Set<String> selectedFields = selectedFields(mapping, representation);

    String id = identity(domain, mapping.idProperty());
    String lid = identity(domain, mapping.localIdProperty());
    if (id == null && lid == null) {
      throw new IllegalArgumentException(
          "Mapped resource has neither id nor lid: " + backend.rawClass(effectiveType).getName());
    }

    Map<String, @Nullable Object> attributes = new LinkedHashMap<>();
    for (MappingPropertyDefinition<T, P> property : mapping.attributes()) {
      if (selectedFields != null && !selectedFields.contains(property.jsonapiName())) {
        continue;
      }
      MappingValue converted = backend.convertAttribute(domain, mapping, property);
      if (converted.emitted()) {
        attributes.put(property.jsonapiName(), converted.value());
      }
    }

    Map<String, @Nullable Relationship> relationships = new LinkedHashMap<>();
    for (MappingPropertyDefinition<T, P> property : mapping.relationships()) {
      if (selectedFields != null && !selectedFields.contains(property.jsonapiName())) {
        continue;
      }
      Object rawValue = backend.read(domain, property);
      relationships.put(
          property.jsonapiName(),
          Relationship.withData(relationshipData(rawValue, property)));
    }

    return new ResourceObject(
        mapping.resourceType(),
        id,
        lid,
        attributes.isEmpty() ? null : Attributes.ofAttributes(attributes),
        relationships.isEmpty() ? null : Relationships.ofRelationships(relationships),
        null,
        null,
        Map.of());
  }

  private @Nullable Set<String> selectedFields(
      MappingDefinition<T, P> mapping, @Nullable MappingRepresentation representation) {
    if (representation == null) {
      return null;
    }
    Map<String, List<String>> fieldsets = representation.selection().fieldsets();
    if (!fieldsets.containsKey(mapping.resourceType())) {
      return null;
    }
    List<String> fields = fieldsets.get(mapping.resourceType());
    Set<String> mappedNames = new java.util.HashSet<>();
    for (MappingPropertyDefinition<T, P> property : mapping.attributes()) {
      mappedNames.add(property.jsonapiName());
    }
    for (MappingPropertyDefinition<T, P> property : mapping.relationships()) {
      mappedNames.add(property.jsonapiName());
    }
    for (String field : fields) {
      if (!mappedNames.contains(field)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_FIELDSET_FIELD,
            backend.rawClass(mapping.domainType()),
            "Unknown fieldset field '" + field + "' on " + mapping.resourceType());
      }
      if (!representation.policy().fieldPolicy().allows(mapping.resourceType(), field)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_FIELDSET_FIELD,
            backend.rawClass(mapping.domainType()),
            "Fieldset field denied for " + mapping.resourceType() + "." + field);
      }
    }
    return Set.copyOf(fields);
  }

  public ResourceIdentifier identifier(Object domain, T declaredType) {
    T effectiveType = backend.effectiveType(domain, declaredType);
    MappingDefinition<T, P> mapping = backend.mappingFor(effectiveType);
    String id = identity(domain, mapping.idProperty());
    String lid = identity(domain, mapping.localIdProperty());
    if (id == null && lid == null) {
      throw new IllegalArgumentException(
          "Mapped relationship target has neither id nor lid: "
              + backend.rawClass(effectiveType).getName());
    }
    return new ResourceIdentifier(mapping.resourceType(), id, lid, null, Map.of());
  }

  public MappingIncludedResult collectIncluded(
      Object primary,
      T primaryType,
      ResourceObject primaryResource,
      MappingRepresentation representation) {
    return inclusionEngine.collectIncluded(
        List.of(primary), List.of(primaryType), List.of(primaryResource), null, representation);
  }

  private @Nullable String identity(
      Object domain, @Nullable MappingPropertyDefinition<T, P> property) {
    if (property == null) {
      return null;
    }
    return backend.convertIdentifier(backend.read(domain, property));
  }

  private RelationshipData relationshipData(
      @Nullable Object rawValue, MappingPropertyDefinition<T, P> property) {
    T targetType = backend.relationshipTargetType(property);
    List<Object> values = backend.relationshipValues(rawValue, property);

    if (property.toMany()) {
      List<ResourceIdentifier> identifiers = new ArrayList<>(values.size());
      for (Object value : values) {
        if (value != null) {
          identifiers.add(identifier(value, targetType));
        }
      }
      return new RelationshipData.IdentifierCollectionLinkage(identifiers);
    }

    if (values.isEmpty() || values.getFirst() == null) {
      return RelationshipData.NullLinkage.INSTANCE;
    }
    return new RelationshipData.SingleLinkage(identifier(values.getFirst(), targetType));
  }

  private final class InclusionBackend implements InclusionMappingBackend<T> {

    @Override
    public Class<?> rawClass(T type) {
      return backend.rawClass(type);
    }

    @Override
    public String resourceType(T type) {
      return backend.mappingFor(type).resourceType();
    }

    @Override
    public java.util.Optional<T> relatedType(
        T ownerType, String relationshipName, String dottedPath) {
      MappingPropertyDefinition<T, P> relationship =
          relationship(ownerType, relationshipName);
      return relationship == null
          ? java.util.Optional.empty()
          : java.util.Optional.of(backend.relationshipTargetType(relationship));
    }

    @Override
    public List<Object> relatedValues(Object domain, T ownerType, String relationshipName) {
      MappingPropertyDefinition<T, P> relationship =
          Objects.requireNonNull(
              relationship(ownerType, relationshipName), "validated relationship");
      return backend.relationshipValues(backend.read(domain, relationship), relationship);
    }

    @Override
    public T effectiveType(Object domain, T declaredType) {
      return backend.effectiveType(domain, declaredType);
    }

    @Override
    public ResourceIdentifier identifier(Object domain, T type) {
      return GenericDomainResourceWriter.this.identifier(domain, type);
    }

    @Override
    public ResourceObject render(
        Object domain, T type, MappingRepresentation representation) {
      return GenericDomainResourceWriter.this.toResource(domain, type, representation);
    }

    @Override
    public boolean hasIdentity(Object domain, T type) {
      MappingDefinition<T, P> mapping = backend.mappingFor(backend.effectiveType(domain, type));
      return identity(domain, mapping.idProperty()) != null
          || identity(domain, mapping.localIdProperty()) != null;
    }

    private @Nullable MappingPropertyDefinition<T, P> relationship(
        T ownerType, String relationshipName) {
      for (MappingPropertyDefinition<T, P> property : backend.mappingFor(ownerType).relationships()) {
        if (property.jsonapiName().equals(relationshipName)) {
          return property;
        }
      }
      return null;
    }
  }
}
