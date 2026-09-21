package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.mapping.internal.SemanticProperty;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

record MappingProperty(
    BeanPropertyDefinition definition, AnnotatedMember accessor, SemanticProperty metadata)
    implements MappingPropertyView {

  @Override
  public tools.jackson.databind.JavaType type() {
    return accessor.getType();
  }
}
