package com.kazforge.jsonapi.jackson3.internal.mapping;

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
