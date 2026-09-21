package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.SemanticProperty;

record MappingProperty(
    BeanPropertyDefinition definition, AnnotatedMember accessor, SemanticProperty metadata)
    implements MappingPropertyView {

  @Override
  public JavaType type() {
    return accessor.getType();
  }
}
