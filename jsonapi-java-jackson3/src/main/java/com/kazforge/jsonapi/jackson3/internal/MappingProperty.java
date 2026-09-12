package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.jackson.internal.mapping.PropertyRole;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

record MappingProperty(
    BeanPropertyDefinition definition,
    AnnotatedMember accessor,
    String logicalName,
    String jsonapiName,
    PropertyRole role)
    implements MappingPropertyView {

  @Override
  public tools.jackson.databind.JavaType type() {
    return accessor.getType();
  }
}
