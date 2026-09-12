package com.kazforge.jsonapi.query;

/** Direction of one JSON:API sort field. */
public enum SortDirection {
  ASCENDING,
  DESCENDING;

  /** Short alias for {@link #ASCENDING}. */
  public static final SortDirection ASC = ASCENDING;

  /** Short alias for {@link #DESCENDING}. */
  public static final SortDirection DESC = DESCENDING;
}
