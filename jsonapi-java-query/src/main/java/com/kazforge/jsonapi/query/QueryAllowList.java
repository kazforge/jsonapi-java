package com.kazforge.jsonapi.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable exact allow-list for parsed include paths, sparse fields, and sort fields.
 *
 * <p>Supplying an instance to {@link JsonApiQueryParser} restricts every named selection category.
 * An empty set rejects every non-empty selection in that category. Empty include and fieldset
 * requests contain no named selection and therefore remain valid.
 */
public record QueryAllowList(
    Set<String> includePaths,
    Set<String> sortFields,
    Map<String, Set<String>> fieldsByResourceType) {

  public QueryAllowList {
    includePaths = copySet(includePaths, "includePaths");
    sortFields = copySet(sortFields, "sortFields");
    fieldsByResourceType = copyFieldsByResourceType(fieldsByResourceType);
  }

  /** Creates an allow-list with fields grouped before sort fields. */
  public QueryAllowList(
      Set<String> includePaths,
      Map<String, ? extends Set<String>> fieldsByResourceType,
      Set<String> sortFields) {
    this(includePaths, sortFields, copyFieldsByResourceType(fieldsByResourceType));
  }

  public static QueryAllowList of(
      Set<String> includePaths,
      Set<String> sortFields,
      Map<String, ? extends Set<String>> fieldsByResourceType) {
    return new QueryAllowList(
        includePaths, sortFields, copyFieldsByResourceType(fieldsByResourceType));
  }

  /** Creates an allow-list with fields grouped before sort fields. */
  public static QueryAllowList of(
      Set<String> includePaths,
      Map<String, ? extends Set<String>> fieldsByResourceType,
      Set<String> sortFields) {
    return new QueryAllowList(
        includePaths, sortFields, copyFieldsByResourceType(fieldsByResourceType));
  }

  /** Returns an allow-list that rejects every named selection. */
  public static QueryAllowList empty() {
    return new QueryAllowList(Set.of(), Set.of(), Map.of());
  }

  /** Alias emphasizing that these are allowed include paths. */
  public Set<String> allowedIncludePaths() {
    return includePaths;
  }

  /** Alias emphasizing that these are allowed sort fields. */
  public Set<String> allowedSortFields() {
    return sortFields;
  }

  /** Alias for the resource-type keyed field allow-list. */
  public Map<String, Set<String>> fieldNamesByResourceType() {
    return fieldsByResourceType;
  }

  boolean allowsIncludePath(String path) {
    return includePaths.contains(path);
  }

  boolean allowsSortField(String field) {
    return sortFields.contains(field);
  }

  boolean allowsField(String resourceType, String field) {
    Set<String> fields = fieldsByResourceType.get(resourceType);
    return fields != null && fields.contains(field);
  }

  private static Set<String> copySet(Set<String> values, String name) {
    Objects.requireNonNull(values, name);
    LinkedHashSet<String> copy = new LinkedHashSet<>();
    for (String value : values) {
      copy.add(Objects.requireNonNull(value, name + " member"));
    }
    return Collections.unmodifiableSet(copy);
  }

  private static Map<String, Set<String>> copyFieldsByResourceType(
      Map<String, ? extends Set<String>> values) {
    Objects.requireNonNull(values, "fieldsByResourceType");
    Map<String, Set<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends Set<String>> entry : values.entrySet()) {
      String resourceType = Objects.requireNonNull(entry.getKey(), "field resource type");
      copy.put(resourceType, copySet(entry.getValue(), "fields for resource type " + resourceType));
    }
    return Collections.unmodifiableMap(copy);
  }
}
