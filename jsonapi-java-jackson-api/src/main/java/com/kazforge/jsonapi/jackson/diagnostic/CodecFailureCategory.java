package com.kazforge.jsonapi.jackson.diagnostic;

/** Stable failure category for {@link JsonApiDocumentReadException}. */
public enum CodecFailureCategory {

  /** Input is not well-formed JSON. */
  MALFORMED_JSON,

  /** A JSON token was present where a different token kind was required. */
  UNEXPECTED_TOKEN,

  /** A JSON object repeated the same member name. */
  DUPLICATE_MEMBER,

  /** A public core constructor rejected the value (local validation). */
  LOCAL_VALIDATION,

  /**
   * {@link com.kazforge.jsonapi.core.validation.JsonApiDocumentValidator} rejected the document.
   */
  AGGREGATE_VALIDATION
}
