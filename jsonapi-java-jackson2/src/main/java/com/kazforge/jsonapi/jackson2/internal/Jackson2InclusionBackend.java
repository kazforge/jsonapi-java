package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.mapping.internal.EffectiveRepresentation;
import com.kazforge.jsonapi.mapping.internal.InclusionBackend;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Jackson 2 bridge that exposes only the native capabilities the shared compound-inclusion engine
 * cannot own: {@code JavaType} resolution, mapping/property lookup, property reads, declared
 * to-many cardinality, identity extraction, and selective rendering.
 */
public final class Jackson2InclusionBackend implements InclusionBackend<JavaType> {

  private final DomainResourceWriter writer;

  public Jackson2InclusionBackend(DomainResourceWriter writer) {
    this.writer = Objects.requireNonNull(writer, "writer");
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
  public boolean hasRelationship(JavaType ownerType, String relationshipName) {
    return findRelationship(writer.mappingFor(ownerType), relationshipName) != null;
  }

  @Override
  public JavaType relatedType(JavaType ownerType, String relationshipName, String dottedPath) {
    MappingProperty property = requireRelationship(ownerType, relationshipName);
    JavaType propertyType = property.accessor().getType();
    JavaType relatedType = unwrapOptionalType(propertyType);
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

  @Override
  public @Nullable Object relationshipValue(
      Object domain, JavaType ownerType, String relationshipName) {
    MappingProperty property = requireRelationship(ownerType, relationshipName);
    return writer.readRelationshipValue(domain, property);
  }

  @Override
  public boolean relationshipToMany(JavaType ownerType, String relationshipName) {
    MappingProperty property = requireRelationship(ownerType, relationshipName);
    return MappingTypeSupport.isToManyType(unwrapOptionalType(property.accessor().getType()));
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
      Object domain, JavaType type, EffectiveRepresentation representation) {
    return writer.toResource(domain, type, representation);
  }

  @Override
  public boolean hasIdentity(Object domain, JavaType type) {
    return writer.hasIdentity(domain, type);
  }

  private MappingProperty requireRelationship(JavaType ownerType, String relationshipName) {
    MappingProperty property = findRelationship(writer.mappingFor(ownerType), relationshipName);
    if (property == null) {
      // The engine asks only after hasRelationship confirmed the JSON:API name.
      throw new IllegalArgumentException(
          "Unknown relationship '" + relationshipName + "' on " + resourceType(ownerType));
    }
    return property;
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

  private static JavaType unwrapOptionalType(JavaType type) {
    if (type.isTypeOrSubTypeOf(Optional.class) && type.containedTypeCount() > 0) {
      return type.containedType(0);
    }
    return type;
  }
}
