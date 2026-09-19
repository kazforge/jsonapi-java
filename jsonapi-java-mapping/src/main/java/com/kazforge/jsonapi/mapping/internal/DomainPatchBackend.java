package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import org.jspecify.annotations.Nullable;

/** Backend conversions required by the shared low-level PATCH command PoC. */
public interface DomainPatchBackend<T, P> {

  Class<?> rawClass(T type);

  MappingDefinition<T, P> mappingFor(T type);

  Object convertPatchIdentity(
      String wireIdentifier,
      MappingDefinition<T, P> mapping,
      MappingPropertyDefinition<T, P> property);

  @Nullable Object convertPatchAttribute(
      @Nullable Object rawValue,
      MappingDefinition<T, P> mapping,
      MappingPropertyDefinition<T, P> property);

  @Nullable Object convertPatchRelationship(
      RelationshipData data,
      MappingDefinition<T, P> mapping,
      MappingPropertyDefinition<T, P> property);
}
