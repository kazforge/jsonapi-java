package com.kazforge.jsonapi.mapping.internal;

/**
 * Backend-neutral JSON:API role carried by adapter-local mapping metadata.
 *
 * <p>{@link #RELATIONSHIP_META} is valid only on resolved metadata: an unresolved declaration names
 * its target relationship by logical identity until the resolver matches it. See {@link
 * SemanticProperty}.
 */
public enum PropertyRole {
  ID,
  LOCAL_ID,
  ATTRIBUTE,
  RELATIONSHIP,
  RESOURCE_META,
  RELATIONSHIP_META
}
