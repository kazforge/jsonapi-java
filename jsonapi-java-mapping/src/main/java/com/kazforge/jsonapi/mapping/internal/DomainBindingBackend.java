package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Minimal construction capabilities required by the shared Core-to-application binding PoC.
 */
public interface DomainBindingBackend<T, P> {

  T constructType(Class<?> rawType);

  Class<?> rawClass(T type);

  BindingDefinition<T, P> bindingFor(T type);

  @Nullable Object parseIdentifier(String wireIdentifier);

  @Nullable Object convertRelationship(
      RelationshipData data, BindingPropertyDefinition<T, P> property);

  Object construct(Map<String, @Nullable Object> properties, T targetType);
}
