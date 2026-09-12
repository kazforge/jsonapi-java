package com.kazforge.jsonapi.jackson.internal.mapping;

/** Neutral JSON:API role carried by adapter-local mapping metadata. */
public enum PropertyRole {
  ID,
  LOCAL_ID,
  ATTRIBUTE,
  RELATIONSHIP,
  RESOURCE_META,
  RELATIONSHIP_META
}
