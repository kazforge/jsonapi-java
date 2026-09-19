package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.mapping.internal.InclusionMappingBackend;
import com.kazforge.jsonapi.jackson.internal.representation.EffectiveRepresentation;
import com.kazforge.jsonapi.mapping.internal.MappingRepresentation;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

/**
 * jackson3 bridge for the backend-neutral compound-inclusion engine.
 *
 * <p>Only mapper-specific type/property mechanics belong here; JSON:API traversal semantics live in
 * the shared engine.
 */
final class Jackson3InclusionMappingBackend
    implements InclusionMappingBackend<JavaType> {

  private final DomainResourceWriter writer;

  Jackson3InclusionMappingBackend(DomainResourceWriter writer) {
    this.writer = writer;
  }

  @Override
  public Class<?> rawClass(JavaType type) {
    return type.getRawClass();
  }

  @Override
  public String resourceType(JavaType type) {
    return writer.mappingFor(type).resourceType();
  }

  @Override
  public Optional<JavaType> relatedType(
      JavaType ownerType, String relationshipName, String dottedPath) {
    ResourceMapping mapping = writer.mappingFor(ownerType);
    MappingProperty property = findRelationship(mapping, relationshipName);
    if (property == null) {
      return Optional.empty();
    }
    return Optional.of(resolveRelatedDomainType(property, ownerType, dottedPath));
  }

  @Override
  public List<Object> relatedValues(
      Object domain, JavaType ownerType, String relationshipName) {
    ResourceMapping mapping = writer.mappingFor(ownerType);
    MappingProperty property = findRelationship(mapping, relationshipName);
    if (property == null) {
      throw new IllegalStateException(
          "Relationship was validated but is no longer mapped: " + relationshipName);
    }

    Object raw = writer.readRelationshipValue(domain, property);
    Object value = DomainResourceWriter.unwrapOptional(raw);
    JavaType propertyType = unwrapOptionalType(property.accessor().getType());

    if (MappingTypeSupport.isToManyType(propertyType)) {
      if (value == null) {
        return List.of();
      }
      List<Object> domainObjects = new ArrayList<>();
      for (Object item : DomainResourceWriter.convertToCollection(value)) {
        Object unwrapped = DomainResourceWriter.unwrapOptional(item);
        if (unwrapped instanceof RelationshipLinkage<?, ?>(Object target, Object ignored)) {
          unwrapped = target;
        }
        if (isIncludableDomainObject(unwrapped)) {
          domainObjects.add(unwrapped);
        }
      }
      return List.copyOf(domainObjects);
    }

    if (value instanceof RelationshipLinkage<?, ?>(Object target, Object ignored)) {
      value = target;
    }
    return isIncludableDomainObject(value) ? List.of(value) : List.of();
  }

  @Override
  public JavaType effectiveType(Object domain, JavaType declaredType) {
    return writer.effectiveType(domain, declaredType);
  }

  @Override
  public ResourceIdentifier identifier(Object domain, JavaType type) {
    return writer.extractIdentifier(domain, type);
  }

  @Override
  public ResourceObject render(
      Object domain, JavaType type, MappingRepresentation representation) {
    return writer.toResource(
        domain,
        type,
        new EffectiveRepresentation(representation.selection(), representation.policy()));
  }

  @Override
  public boolean hasIdentity(Object domain, JavaType type) {
    ResourceMapping mapping = writer.mappingFor(type);
    return writer.extractId(domain, mapping) != null
        || writer.extractLocalId(domain, mapping) != null;
  }

  private static @Nullable MappingProperty findRelationship(
      ResourceMapping mapping, String jsonapiName) {
    for (MappingProperty property : mapping.relationships()) {
      if (property.jsonapiName().equals(jsonapiName)) {
        return property;
      }
    }
    return null;
  }

  private static JavaType resolveRelatedDomainType(
      MappingProperty property, JavaType ownerType, String dottedPath) {
    JavaType relatedType = unwrapOptionalType(property.accessor().getType());
    if (MappingTypeSupport.isToManyType(relatedType)) {
      JavaType contentType = MappingTypeSupport.resolveContentType(relatedType);
      if (contentType == null) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
            ownerType.getRawClass(),
            "Cannot resolve collection content type for include path '" + dottedPath + "'");
      }
      relatedType = unwrapOptionalType(contentType);
    }

    JavaType linkageType = MappingTypeSupport.linkageJavaType(relatedType);
    if (linkageType != null) {
      relatedType =
          MappingTypeSupport.unwrapOptionalType(MappingTypeSupport.linkageTargetType(linkageType));
    }
    return relatedType;
  }

  private static JavaType unwrapOptionalType(JavaType type) {
    if (type.isTypeOrSubTypeOf(Optional.class) && type.containedTypeCount() > 0) {
      return type.containedType(0);
    }
    return type;
  }

  private static boolean isIncludableDomainObject(@Nullable Object value) {
    return value != null
        && !(value instanceof ResourceIdentifier)
        && !(value instanceof RelationshipData)
        && !(value instanceof RelationshipLinkage<?, ?>);
  }
}
