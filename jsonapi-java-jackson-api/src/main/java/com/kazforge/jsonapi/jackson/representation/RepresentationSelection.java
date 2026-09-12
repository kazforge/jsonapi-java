package com.kazforge.jsonapi.jackson.representation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable per-operation selection of include paths and sparse fieldsets.
 *
 * <p>An absent include request ({@code include} was not supplied) omits {@code included}. An
 * explicitly requested empty include emits {@code included: []} when no resources resolve, and a
 * non-empty include-path list that resolves to no resources does the same. An absent fieldset type
 * leaves its attributes and relationships unrestricted; a present type with an empty list selects
 * none. Include-path segments and field names are JSON:API external member names resolved by the
 * configured Jackson mapper, never Java logical property names.
 *
 * <p>This value contains representation-shaping requests only. It does not model filters, sorting,
 * pagination, persistence projections, authorization state, or query predicates.
 */
public final class RepresentationSelection {

  private static final RepresentationSelection NONE =
      new RepresentationSelection(List.of(), Map.of(), false);

  private final List<IncludePath> includePaths;
  private final Map<String, List<String>> fieldsets;
  private final boolean includeRequested;

  private RepresentationSelection(
      List<IncludePath> includePaths,
      Map<String, List<String>> fieldsets,
      boolean includeRequested) {
    this.includePaths = List.copyOf(includePaths);
    this.fieldsets = copyFieldsets(fieldsets);
    this.includeRequested = includeRequested;
  }

  /** Returns the selection that requests neither inclusion nor sparse fieldsets. */
  public static RepresentationSelection none() {
    return NONE;
  }

  /** Creates a builder for one immutable selection. */
  public static Builder builder() {
    return new Builder();
  }

  /** Ordered include paths requested for compound-document traversal. */
  public List<IncludePath> includePaths() {
    return includePaths;
  }

  /** Returns whether the request explicitly supplied an {@code include} parameter. */
  public boolean includeRequested() {
    return includeRequested;
  }

  /** Sparse field names by JSON:API resource type, including explicitly empty fieldsets. */
  public Map<String, List<String>> fieldsets() {
    return fieldsets;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof RepresentationSelection selection
        && includePaths.equals(selection.includePaths)
        && fieldsets.equals(selection.fieldsets)
        && includeRequested == selection.includeRequested;
  }

  @Override
  public int hashCode() {
    return Objects.hash(includePaths, fieldsets, includeRequested);
  }

  private static Map<String, List<String>> copyFieldsets(Map<String, List<String>> fieldsets) {
    Objects.requireNonNull(fieldsets, "fieldsets");
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : fieldsets.entrySet()) {
      String resourceType = Objects.requireNonNull(entry.getKey(), "fieldset resource type");
      List<String> fields = Objects.requireNonNull(entry.getValue(), "fieldset fields");
      LinkedHashSet<String> unique = new LinkedHashSet<>();
      for (String fieldName : fields) {
        unique.add(Objects.requireNonNull(fieldName, "fieldset field name"));
      }
      copy.put(resourceType, List.copyOf(unique));
    }
    return Collections.unmodifiableMap(copy);
  }

  /** Mutable builder whose {@link #build()} result defensively copies all supplied values. */
  public static final class Builder {
    private final List<IncludePath> includePaths = new java.util.ArrayList<>();
    private final Map<String, LinkedHashSet<String>> fieldsets = new LinkedHashMap<>();
    private boolean includeRequested;

    private Builder() {}

    /** Parses and adds one dotted include path using {@link IncludePath}'s validation. */
    public Builder include(String path) {
      return include(IncludePath.of(path));
    }

    /** Adds one already-validated include path. */
    public Builder include(IncludePath path) {
      includeRequested = true;
      includePaths.add(Objects.requireNonNull(path, "path"));
      return this;
    }

    /** Records an explicit include request without adding an include path. */
    public Builder includeRequested() {
      includeRequested = true;
      return this;
    }

    /** Selects fields for one resource type. Repeated names retain their first-seen order. */
    public Builder fields(String resourceType, String... fieldNames) {
      Objects.requireNonNull(fieldNames, "fieldNames");
      return fields(resourceType, List.of(fieldNames));
    }

    /** Selects fields for one resource type. Repeated names retain their first-seen order. */
    public Builder fields(String resourceType, List<String> fieldNames) {
      String nonNullResourceType = Objects.requireNonNull(resourceType, "resourceType");
      List<String> nonNullFieldNames = Objects.requireNonNull(fieldNames, "fieldNames");
      LinkedHashSet<String> fields =
          fieldsets.computeIfAbsent(nonNullResourceType, ignored -> new LinkedHashSet<>());
      for (String fieldName : nonNullFieldNames) {
        fields.add(Objects.requireNonNull(fieldName, "fieldset field name"));
      }
      return this;
    }

    /** Builds an immutable representation selection. */
    public RepresentationSelection build() {
      if (includePaths.isEmpty() && fieldsets.isEmpty() && !includeRequested) {
        return NONE;
      }
      Map<String, List<String>> copiedFieldsets = new LinkedHashMap<>();
      for (Map.Entry<String, LinkedHashSet<String>> entry : fieldsets.entrySet()) {
        copiedFieldsets.put(entry.getKey(), List.copyOf(entry.getValue()));
      }
      return new RepresentationSelection(includePaths, copiedFieldsets, includeRequested);
    }
  }
}
