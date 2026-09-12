package com.kazforge.jsonapi.jackson.representation;

import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import java.util.List;
import java.util.Objects;

/**
 * An ordered include path of JSON:API relationship names.
 *
 * <p>Use {@link #of(String)} to parse a dotted path such as {@code comments.author}. Syntax
 * failures throw {@link JsonApiMappingException} with {@link
 * MappingDiagnostic#INVALID_INCLUDE_PATH}.
 */
public record IncludePath(List<String> segments) {

  public IncludePath {
    Objects.requireNonNull(segments, "segments");
    if (segments.isEmpty()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_INCLUDE_PATH,
          "Include path must contain at least one relationship name");
    }
    for (String segment : segments) {
      validateSegment(segment, String.join(".", segments));
    }
    segments = List.copyOf(segments);
  }

  /**
   * Parses a dotted include path. Does not trim or otherwise normalize input. Rejects empty input,
   * leading or trailing dots, repeated dots, and whitespace-only segments.
   */
  public static IncludePath of(String path) {
    Objects.requireNonNull(path, "path");
    String[] parts = path.split("\\.", -1);
    for (String part : parts) {
      if (part.isEmpty() || isWhitespaceOnly(part)) {
        throw new JsonApiMappingException(
            MappingDiagnostic.INVALID_INCLUDE_PATH, "Malformed include path: '" + path + "'");
      }
    }
    return new IncludePath(List.of(parts));
  }

  private static void validateSegment(String segment, String propertyPath) {
    Objects.requireNonNull(segment, "segment");
    if (segment.isEmpty() || isWhitespaceOnly(segment) || segment.indexOf('.') >= 0) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_INCLUDE_PATH,
          "Invalid include path segment: '" + segment + "' in '" + propertyPath + "'");
    }
  }

  private static boolean isWhitespaceOnly(String segment) {
    if (segment.isEmpty()) {
      return false;
    }
    for (int i = 0; i < segment.length(); i++) {
      if (!Character.isWhitespace(segment.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /** Returns the dotted JSON:API form of this path (for diagnostics). */
  public String dotted() {
    return String.join(".", segments);
  }

  /** Returns the dotted form of the path prefix through {@code segmentIndex} (inclusive). */
  public String dottedThrough(int segmentIndex) {
    if (segmentIndex < 0 || segmentIndex >= segments.size()) {
      throw new IndexOutOfBoundsException("segmentIndex=" + segmentIndex);
    }
    return String.join(".", segments.subList(0, segmentIndex + 1));
  }
}
