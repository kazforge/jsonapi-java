package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.github.kazemek.jsonapi.jackson.internal.mapping.PropertyRole;

record MappingProperty(
    BeanPropertyDefinition definition,
    AnnotatedMember accessor,
    String logicalName,
    String jsonapiName,
    PropertyRole role)
    implements MappingPropertyView {

  @Override
  public JavaType type() {
    return accessor.getType();
  }
}
