package io.github.kazemek.jsonapi.query;

import java.util.Objects;

/** Immutable direction plus the directionless JSON:API field token from one sort item. */
public record SortField(String field, SortDirection direction) {

  public SortField {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(direction, "direction");
  }

  /** Creates an ascending sort field. */
  public static SortField ascending(String field) {
    return new SortField(field, SortDirection.ASCENDING);
  }

  /** Creates a descending sort field. */
  public static SortField descending(String field) {
    return new SortField(field, SortDirection.DESCENDING);
  }

  /** Alias for constructing an ascending sort field. */
  public static SortField of(String field) {
    return ascending(field);
  }

  /** Alias for constructing a sort field with an explicit direction. */
  public static SortField of(String field, SortDirection direction) {
    return new SortField(field, direction);
  }

  /** Returns the directionless field token. */
  public String fieldName() {
    return field;
  }

  /** Alias for {@link #field()}. */
  public String name() {
    return field;
  }
}
