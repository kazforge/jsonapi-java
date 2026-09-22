package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.SemanticProperty;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One JSON:API-mapped property from Jackson's effective deserialization view.
 *
 * <p>{@code effectiveProperty} is present only when the configured mapper's bean deserializer has a
 * bindable property for the logical name; it is the single authority for the effective
 * deserialization type, creator participation, injection-only exclusion, and active-view
 * visibility. A serialization-only declaration may therefore remain in the read mapping for
 * supplied-member diagnostics without becoming bindable, and its serialization-side primary type is
 * never used as a construction-path fallback.
 */
public record ReadMappingProperty(
    BeanPropertyDefinition definition,
    @Nullable AnnotatedMember serializationMember,
    @Nullable SettableBeanProperty effectiveProperty,
    SemanticProperty metadata)
    implements MappingPropertyView {

  boolean deserializable() {
    return effectiveProperty != null;
  }

  /**
   * The effective property for a bindable read property. Only construction starts, which are
   * emitted for bindable properties, call this.
   */
  SettableBeanProperty effectivePropertyOrThrow() {
    return Objects.requireNonNull(effectiveProperty, "effectiveProperty");
  }

  @Override
  public JavaType type() {
    return effectiveProperty != null ? effectiveProperty.getType() : definition.getPrimaryType();
  }
}
