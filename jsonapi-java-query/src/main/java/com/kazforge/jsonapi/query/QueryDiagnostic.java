package com.kazforge.jsonapi.query;

/**
 * Stable diagnostic codes for query-parameter parsing failures.
 *
 * <p>Consumers may branch on these codes and use {@link JsonApiQueryException#parameterName()} for
 * attribution. Exception messages are explanatory text, not a stable machine-readable contract.
 */
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
