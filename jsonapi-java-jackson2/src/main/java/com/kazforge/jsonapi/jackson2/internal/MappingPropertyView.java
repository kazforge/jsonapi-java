package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.SemanticPropertyCarrier;

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
