package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Neutral read-side mapping definition produced from a concrete JSON-library backend. */
public record BindingDefinition<T, P>(
    String resourceType,
    T domainType,
    @Nullable BindingPropertyDefinition<T, P> idProperty,
    @Nullable BindingPropertyDefinition<T, P> localIdProperty,
    List<BindingPropertyDefinition<T, P>> attributes,
    List<BindingPropertyDefinition<T, P>> relationships) {

  public BindingDefinition {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(domainType, "domainType");
    attributes = List.copyOf(attributes);
    relationships = List.copyOf(relationships);
  }
}
