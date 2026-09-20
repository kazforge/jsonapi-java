package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Minimal JSON-library capability boundary required by the shared resource-mapping PoC.
 *
 * <p>This intentionally does not model parser, tree, serializer, reflection, or bean APIs. Concrete
 * backends retain those mechanics and expose only resolved jsonapi-java mapping capabilities.
 */
public interface DomainMappingBackend<T, P> {

  T inferredType(Object domain);

  T effectiveType(Object domain, T declaredType);

  Class<?> rawClass(T type);

  MappingDefinition<T, P> mappingFor(T type);

  @Nullable Object read(Object domain, MappingPropertyDefinition<T, P> property);

  MappingValue convertAttribute(
      Object domain, MappingDefinition<T, P> mapping, MappingPropertyDefinition<T, P> property);

  @Nullable String convertIdentifier(@Nullable Object value);

  T relationshipTargetType(MappingPropertyDefinition<T, P> property);

  List<Object> relationshipValues(
      @Nullable Object rawValue, MappingPropertyDefinition<T, P> property);
}
