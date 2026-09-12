package com.kazforge.jsonapi.core.validation;

/** Declares how a document is used for context-sensitive validation. */
public enum DocumentUsage {
  /**
   * Base-spec create-resource request: primary data must be a single resource object whose {@code
   * id} may be omitted, and every relationship supplied on that resource must contain {@code data}.
   * Included resources are exempt from the primary relationship-data rule; otherwise existing rules
   * apply unchanged. Core itself is not HTTP-method-aware; a future server layer selects this usage
   * from its own operation context.
   */
  CREATE_REQUEST,
  /** Base-spec update request: single-resource primary data with required {@code id}. */
  UPDATE_REQUEST,
  /** Response or any other document use. */
  RESPONSE_OR_OTHER
}
