package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Direction-neutral view of one JSON:API-mapped Jackson property.
 *
 * <p>{@link #logicalName()} is the Jackson internal property identity (Java field, record
 * component, or JavaBean name). {@link #jsonapiName()} is the JSON:API member name on the wire:
 * configured Jackson's external name for attributes and relationships, and the target
 * relationship's external name for relationship meta.
 */
interface MappingPropertyView {

  BeanPropertyDefinition definition();

  String logicalName();

  String jsonapiName();

  PropertyRole role();

  JavaType type();
}
