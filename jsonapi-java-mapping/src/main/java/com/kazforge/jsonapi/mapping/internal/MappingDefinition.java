package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Neutral JSON:API mapping definition produced by one concrete JSON-library backend. */
public record MappingDefinition<T, P>(
    String resourceType,
    T domainType,
    @Nullable MappingPropertyDefinition<T, P> idProperty,
    @Nullable MappingPropertyDefinition<T, P> localIdProperty,
    List<MappingPropertyDefinition<T, P>> attributes,
    List<MappingPropertyDefinition<T, P>> relationships) {

  public MappingDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(domainType, "domainType");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
  }
}
