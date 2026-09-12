package com.kazforge.jsonapi.query;

/** Stable diagnostic codes for query-parameter parsing failures. */
public enum QueryDiagnostic {
  MALFORMED_ENCODING,
  INVALID_PARAMETER_SHAPE,
  INVALID_PARAMETER_CARDINALITY,
  INVALID_INCLUDE_SYNTAX,
  INVALID_FIELDSET_SYNTAX,
  INVALID_SORT_SYNTAX,
  DISALLOWED_INCLUDE_PATH,
  DISALLOWED_FIELD,
  DISALLOWED_SORT_FIELD
}
