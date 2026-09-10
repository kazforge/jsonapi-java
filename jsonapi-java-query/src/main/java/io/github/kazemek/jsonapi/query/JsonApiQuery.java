package io.github.kazemek.jsonapi.query;

import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of parsing one JSON:API query string or decoded parameter multimap.
 *
 * <p>Page, filter, and unprocessed parameters are intentionally opaque. Their ordered maps retain
 * parameter insertion order and each value list retains occurrence order.
 */
public record JsonApiQuery(
    RepresentationSelection selection,
    List<SortField> sortFields,
    Map<String, List<String>> pageParameters,
    Map<String, List<String>> filterParameters,
    Map<String, List<String>> unprocessedParameters) {
  public JsonApiQuery {
    Objects.requireNonNull(selection, "selection");
    sortFields = List.copyOf(Objects.requireNonNull(sortFields, "sortFields"));
    pageParameters = copyParameters(pageParameters, "pageParameters");
    filterParameters = copyParameters(filterParameters, "filterParameters");
    unprocessedParameters = copyParameters(unprocessedParameters, "unprocessedParameters");
  }

  /** Alias for {@link #selection()}. */
  public RepresentationSelection representationSelection() {
    return selection;
  }

  /** Alias for {@link #sortFields()}. */
  public List<SortField> sort() {
    return sortFields;
  }

  /** Alias for {@link #pageParameters()}. */
  public Map<String, List<String>> page() {
    return pageParameters;
  }

  /** Alias for {@link #filterParameters()}. */
  public Map<String, List<String>> filter() {
    return filterParameters;
  }

  /** Alias for {@link #unprocessedParameters()}. */
  public Map<String, List<String>> unprocessed() {
    return unprocessedParameters;
  }

  private static Map<String, List<String>> copyParameters(
      Map<String, List<String>> parameters, String name) {
    Objects.requireNonNull(parameters, name);
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
      String parameterName = Objects.requireNonNull(entry.getKey(), name + " name");
      List<String> values = Objects.requireNonNull(entry.getValue(), name + " values");
      List<String> valueCopy = List.copyOf(values);
      copy.put(parameterName, valueCopy);
    }
    return Collections.unmodifiableMap(copy);
  }
}
