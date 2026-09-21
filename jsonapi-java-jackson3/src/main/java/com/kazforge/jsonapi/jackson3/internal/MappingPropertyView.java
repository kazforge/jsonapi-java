package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import com.kazforge.jsonapi.mapping.internal.SemanticProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Direction-neutral view of one JSON:API-mapped Jackson property.
 *
 * <p>The write mapping and the flat-read mapping use different Jackson property authorities. This
 * view keeps their shared JSON:API role and naming metadata together without making a serialization
 * accessor proof of deserialization bindability. The stored {@link SemanticProperty} is the single
 * authority for role and names: the logical Jackson identity, the configured Jackson external name
 * used for bean construction, and the JSON:API member name on the wire.
 */
interface MappingPropertyView {

  BeanPropertyDefinition definition();

  SemanticProperty metadata();

  JavaType type();

  /** Jackson internal property identity (Java field, record component, or JavaBean name). */
  default String logicalName() {
    return metadata().logicalName();
  }

  /** Configured Jackson external name used as the {@code convertValue} map key. */
  default String jacksonName() {
    return metadata().externalName();
  }

  /** JSON:API member name on the wire. */
  default String jsonapiName() {
    return metadata().jsonapiName();
  }

  default PropertyRole role() {
    return metadata().role();
  }
}
