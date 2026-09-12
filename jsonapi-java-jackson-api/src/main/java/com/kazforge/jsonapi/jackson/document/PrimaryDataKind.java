package com.kazforge.jsonapi.jackson.document;

/**
 * Explicit interpretation of top-level primary {@code data} objects and arrays.
 *
 * <p>Minimal resource and resource-identifier JSON forms can be identical ({@code {"type","id"}}
 * and {@code []}). Callers must choose the kind; the reader never guesses from object members.
 */
public enum PrimaryDataKind {

  /** Decode primary data as {@code ResourceObject} / resource collections. */
  RESOURCE,

  /** Decode primary data as {@code ResourceIdentifier} / identifier collections. */
  RESOURCE_IDENTIFIER
}
