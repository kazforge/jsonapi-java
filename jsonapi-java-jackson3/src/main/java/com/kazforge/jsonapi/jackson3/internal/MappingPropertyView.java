package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.mapping.internal.SemanticPropertyCarrier;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * Direction-neutral view of one JSON:API-mapped Jackson property.
 *
 * <p>The write mapping and the flat-read mapping use different Jackson property authorities. This
 * view keeps their shared JSON:API role and naming metadata together, through {@link
 * SemanticPropertyCarrier}, without making a serialization accessor proof of deserialization
 * bindability. The stored metadata is the single authority for role and names; the Jackson handles
 * and type model stay adapter-local.
 */
interface MappingPropertyView extends SemanticPropertyCarrier {

  BeanPropertyDefinition definition();

  JavaType type();
}
