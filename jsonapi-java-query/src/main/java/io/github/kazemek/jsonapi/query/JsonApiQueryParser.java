package io.github.kazemek.jsonapi.query;

import io.github.kazemek.jsonapi.core.validation.MemberNames;
import io.github.kazemek.jsonapi.jackson.representation.IncludePath;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Parses JSON:API query selection from either a decoded parameter multimap or a raw query string.
 *
 * <p>The decoded-multimap methods are the canonical parsing seam. Raw input uses UTF-8 form
 * decoding, then invokes that same seam. No value is trimmed, interpreted as a Java property name,
 * or assigned pagination/filter semantics.
 */
public final class JsonApiQueryParser {

  private static final String INCLUDE = "include";
  private static final String SORT = "sort";
  private static final String FIELDS = "fields";
  private static final String PAGE = "page";
  private static final String FILTER = "filter";

  /** Parses a decoded parameter multimap without an allow-list. */
  public JsonApiQuery parse(@Nullable Map<String, List<String>> parameters) {
    return parseDecoded(parameters);
  }

  /** Parses a decoded parameter multimap against an exact selection allow-list. */
  public JsonApiQuery parse(
      @Nullable Map<String, List<String>> parameters, QueryAllowList allowList) {
    return parseDecoded(parameters, allowList);
  }

  /** Parses a raw query string without an allow-list. */
  public JsonApiQuery parse(@Nullable String rawQuery) {
    return parseRaw(rawQuery);
  }

  /** Parses a raw query string against an exact selection allow-list. */
  public JsonApiQuery parse(@Nullable String rawQuery, QueryAllowList allowList) {
    return parseRaw(rawQuery, allowList);
  }

  /** Parses a decoded parameter multimap without an allow-list. */
  public JsonApiQuery parseDecoded(@Nullable Map<String, List<String>> parameters) {
    return parseDecodedInternal(parameters, null);
  }

  /** Parses a decoded parameter multimap against an exact selection allow-list. */
  public JsonApiQuery parseDecoded(
      @Nullable Map<String, List<String>> parameters, QueryAllowList allowList) {
    Objects.requireNonNull(allowList, "allowList");
    return parseDecodedInternal(parameters, allowList);
  }

  /** Parses a raw query string without an allow-list. */
  public JsonApiQuery parseRaw(@Nullable String rawQuery) {
    return parseRawInternal(rawQuery, null);
  }

  /** Parses a raw query string against an exact selection allow-list. */
  public JsonApiQuery parseRaw(@Nullable String rawQuery, QueryAllowList allowList) {
    Objects.requireNonNull(allowList, "allowList");
    return parseRawInternal(rawQuery, allowList);
  }

  private static JsonApiQuery parseRawInternal(
      @Nullable String rawQuery, @Nullable QueryAllowList allowList) {
    if (rawQuery == null) {
      throw invalidShape(null, "Query input must not be null");
    }
    String query = rawQuery.startsWith("?") ? rawQuery.substring(1) : rawQuery;
    if (query.isEmpty()) {
      return parseDecodedInternal(Map.of(), allowList);
    }

    Map<String, List<String>> decoded = new LinkedHashMap<>();
    for (String occurrence : query.split("&", -1)) {
      if (occurrence.isEmpty()) {
        continue;
      }
      int separator = occurrence.indexOf('=');
      String rawName = separator < 0 ? occurrence : occurrence.substring(0, separator);
      String rawValue = separator < 0 ? "" : occurrence.substring(separator + 1);
      String name = decodeName(rawName);
      String value = decodeValue(rawValue, name);
      if (name.isEmpty()) {
        throw invalidShape(name, "Query parameter name must not be empty");
      }
      decoded.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }
    return parseDecodedInternal(decoded, allowList);
  }

  private static String decodeName(String rawName) {
    try {
      return URLDecoder.decode(rawName, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new JsonApiQueryException(
          QueryDiagnostic.MALFORMED_ENCODING,
          null,
          "Malformed percent encoding in query parameter name",
          exception);
    }
  }

  private static String decodeValue(String rawValue, String decodedName) {
    try {
      return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new JsonApiQueryException(
          QueryDiagnostic.MALFORMED_ENCODING,
          decodedName,
          "Malformed percent encoding in query parameter value",
          exception);
    }
  }

  private static JsonApiQuery parseDecodedInternal(
      @Nullable Map<String, List<String>> parameters, @Nullable QueryAllowList allowList) {
    if (parameters == null) {
      throw invalidShape(null, "Decoded query parameter map must not be null");
    }

    Map<String, List<String>> copied = copyDecodedParameters(parameters);
    RepresentationSelection.Builder selection = RepresentationSelection.builder();
    List<SortField> sortFields = new ArrayList<>();
    Map<String, List<String>> page = new LinkedHashMap<>();
    Map<String, List<String>> filter = new LinkedHashMap<>();
    Map<String, List<String>> unprocessed = new LinkedHashMap<>();

    for (Map.Entry<String, List<String>> entry : copied.entrySet()) {
      String name = entry.getKey();
      List<String> values = entry.getValue();
      if (INCLUDE.equals(name)) {
        parseInclude(values, selection, allowList);
      } else if (SORT.equals(name)) {
        parseSort(values, sortFields, allowList);
      } else {
        FieldsetName fieldset = fieldsetName(name);
        if (fieldset != null) {
          parseFieldset(fieldset.resourceType(), name, values, selection, allowList);
        } else if (isFamilyName(name, PAGE)) {
          page.put(name, values);
        } else if (isFamilyName(name, FILTER)) {
          filter.put(name, values);
        } else {
          unprocessed.put(name, values);
        }
      }
    }

    return new JsonApiQuery(selection.build(), sortFields, page, filter, unprocessed);
  }

  private static Map<String, List<String>> copyDecodedParameters(Map<?, ?> parameters) {
    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : parameters.entrySet()) {
      Object rawName = entry.getKey();
      if (!(rawName instanceof String name)) {
        throw invalidShape(null, "Query parameter name must not be null");
      }
      if (name.isEmpty()) {
        throw invalidShape(name, "Query parameter name must not be empty");
      }
      Object rawValues = entry.getValue();
      if (!(rawValues instanceof List<?> values)) {
        throw invalidShape(name, "Query parameter value list must not be null");
      }
      List<String> valueCopy = new ArrayList<>(values.size());
      for (Object rawValue : values) {
        if (!(rawValue instanceof String value)) {
          throw invalidShape(name, "Query parameter value must not be null");
        }
        valueCopy.add(value);
      }
      copy.put(name, List.copyOf(valueCopy));
    }
    return copy;
  }

  private static void parseInclude(
      List<String> values,
      RepresentationSelection.Builder selection,
      @Nullable QueryAllowList allowList) {
    String value = oneValue(INCLUDE, values);
    if (value.isEmpty()) {
      selection.includeRequested();
      return;
    }

    List<String> paths = split(value);
    for (String path : paths) {
      if (!isValidIncludePath(path)) {
        throw queryFailure(
            QueryDiagnostic.INVALID_INCLUDE_SYNTAX,
            INCLUDE,
            "Invalid include path syntax: '" + path + "'");
      }
    }
    for (String path : paths) {
      if (allowList != null && !allowList.allowsIncludePath(path)) {
        throw queryFailure(
            QueryDiagnostic.DISALLOWED_INCLUDE_PATH,
            INCLUDE,
            "Include path is not allowed: '" + path + "'");
      }
      selection.include(new IncludePath(List.of(path.split("\\.", -1))));
    }
  }

  private static boolean isValidIncludePath(String path) {
    if (path.isEmpty()) {
      return false;
    }
    for (String segment : path.split("\\.", -1)) {
      if (!MemberNames.isValid(segment) || isWhitespaceOnly(segment)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isWhitespaceOnly(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      if (!Character.isWhitespace(value.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private static void parseFieldset(
      String resourceType,
      String parameterName,
      List<String> values,
      RepresentationSelection.Builder selection,
      @Nullable QueryAllowList allowList) {
    if (!MemberNames.isValid(resourceType)) {
      throw queryFailure(
          QueryDiagnostic.INVALID_FIELDSET_SYNTAX,
          parameterName,
          "Invalid sparse-fieldset resource type: '" + resourceType + "'");
    }
    String value = oneValue(parameterName, values);
    if (value.isEmpty()) {
      selection.fields(resourceType, List.of());
      return;
    }

    List<String> fields = split(value);
    for (String field : fields) {
      if (!MemberNames.isValid(field)) {
        throw queryFailure(
            QueryDiagnostic.INVALID_FIELDSET_SYNTAX,
            parameterName,
            "Invalid sparse-fieldset field: '" + field + "'");
      }
    }
    for (String field : fields) {
      if (allowList != null && !allowList.allowsField(resourceType, field)) {
        throw queryFailure(
            QueryDiagnostic.DISALLOWED_FIELD,
            parameterName,
            "Sparse field is not allowed: '" + field + "'");
      }
    }
    selection.fields(resourceType, fields);
  }

  private static void parseSort(
      List<String> values, List<SortField> sortFields, @Nullable QueryAllowList allowList) {
    String value = oneValue(SORT, values);
    if (value.isEmpty()) {
      throw queryFailure(QueryDiagnostic.INVALID_SORT_SYNTAX, SORT, "Sort value must not be empty");
    }

    List<String> tokens = split(value);
    List<SortField> parsed = new ArrayList<>();
    for (String token : tokens) {
      boolean descending = token.startsWith("-");
      String field = descending ? token.substring(1) : token;
      if (field.isEmpty() || !MemberNames.isValid(field)) {
        throw queryFailure(
            QueryDiagnostic.INVALID_SORT_SYNTAX,
            SORT,
            "Invalid sort field syntax: '" + token + "'");
      }
      parsed.add(
          new SortField(field, descending ? SortDirection.DESCENDING : SortDirection.ASCENDING));
    }
    for (SortField sortField : parsed) {
      if (allowList != null && !allowList.allowsSortField(sortField.field())) {
        throw queryFailure(
            QueryDiagnostic.DISALLOWED_SORT_FIELD,
            SORT,
            "Sort field is not allowed: '" + sortField.field() + "'");
      }
    }
    sortFields.addAll(parsed);
  }

  private static @Nullable FieldsetName fieldsetName(String name) {
    if (name.equals(FIELDS)) {
      throw invalidShape(name, "Sparse-fieldset parameter must have the form fields[TYPE]");
    }
    if (!name.startsWith(FIELDS + "[")) {
      return null;
    }
    int closingBracket = name.indexOf(']', FIELDS.length() + 1);
    if (closingBracket < 0 || closingBracket != name.length() - 1) {
      throw invalidShape(name, "Sparse-fieldset parameter must have the form fields[TYPE]");
    }
    return new FieldsetName(name.substring(FIELDS.length() + 1, closingBracket));
  }

  private static boolean isFamilyName(String name, String base) {
    if (name.equals(base)) {
      return true;
    }
    if (!name.startsWith(base)) {
      return false;
    }
    int offset = base.length();
    while (offset < name.length()) {
      if (name.charAt(offset) != '[') {
        return false;
      }
      int closingBracket = name.indexOf(']', offset + 1);
      if (closingBracket < 0) {
        return false;
      }
      String component = name.substring(offset + 1, closingBracket);
      if (!component.isEmpty() && !isValidFamilyComponent(component)) {
        return false;
      }
      offset = closingBracket + 1;
    }
    return true;
  }

  private static boolean isValidFamilyComponent(String component) {
    for (String member : component.split("\\.", -1)) {
      if (!MemberNames.isValid(member)) {
        return false;
      }
    }
    return true;
  }

  private static String oneValue(String parameterName, List<String> values) {
    if (values.size() != 1) {
      throw queryFailure(
          QueryDiagnostic.INVALID_PARAMETER_CARDINALITY,
          parameterName,
          "Parameter must have exactly one value");
    }
    return values.getFirst();
  }

  private static List<String> split(String value) {
    return Arrays.asList(value.split(",", -1));
  }

  private static JsonApiQueryException invalidShape(
      @Nullable String parameterName, String message) {
    return new JsonApiQueryException(
        QueryDiagnostic.INVALID_PARAMETER_SHAPE, parameterName, message);
  }

  private static JsonApiQueryException queryFailure(
      QueryDiagnostic diagnostic, String parameterName, String message) {
    return new JsonApiQueryException(diagnostic, parameterName, message);
  }

  private record FieldsetName(String resourceType) {}
}
