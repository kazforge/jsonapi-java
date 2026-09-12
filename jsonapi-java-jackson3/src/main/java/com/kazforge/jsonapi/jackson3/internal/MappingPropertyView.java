package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.jackson.internal.mapping.PropertyRole;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Direction-neutral view of one JSON:API-mapped Jackson property.
 *
 * <p>The write mapping and the flat-read mapping use different Jackson property authorities. This
 * view keeps their shared JSON:API role and wire-name metadata together without making a
 * serialization accessor proof of deserialization bindability.
 *
 * <p>{@link #logicalName()} is the Jackson internal property identity (Java field, record
 * component, or JavaBean name). {@link #jacksonName()} is the configured Jackson external name used
 * for bean construction. {@link #jsonapiName()} is the JSON:API member name on the wire: configured
 * Jackson's external name for attributes and relationships, and the target relationship's external
 * name for relationship meta.
 */
interface MappingPropertyView {

  BeanPropertyDefinition definition();

  String logicalName();

  String jsonapiName();

  PropertyRole role();

  JavaType type();

  /** Configured Jackson external name used as the {@code convertValue} map key. */
  default String jacksonName() {
    return definition().getName();
  }
}
