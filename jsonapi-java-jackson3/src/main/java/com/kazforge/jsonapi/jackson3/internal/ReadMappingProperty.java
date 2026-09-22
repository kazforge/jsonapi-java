package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.mapping.internal.SemanticProperty;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * One JSON:API-mapped property from Jackson's deserialization-oriented view.
 *
 * <p>{@code deserializationType} is present only when the configured mapper has an effective
 * deserialization property for the logical name. A serialization-only declaration may therefore
 * remain in the read mapping for supplied-member diagnostics without becoming bindable.
 */
public record ReadMappingProperty(
    BeanPropertyDefinition definition,
    @Nullable AnnotatedMember serializationMember,
    @Nullable AnnotatedMember deserializationMember,
    @Nullable JavaType deserializationType,
    SemanticProperty metadata)
    implements MappingPropertyView {

  boolean deserializable() {
    return deserializationType != null;
  }

  @Override
  public JavaType type() {
    return deserializationType != null ? deserializationType : definition.getPrimaryType();
  }
}
