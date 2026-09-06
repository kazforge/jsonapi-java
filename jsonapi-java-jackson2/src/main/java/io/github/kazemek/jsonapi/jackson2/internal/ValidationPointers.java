package io.github.kazemek.jsonapi.jackson2.internal;

import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Relocates core model constructors' hardcoded JSON Pointers onto the wire reader's accumulator
 * path so nested failures report the enclosing document location.
 */
final class ValidationPointers {

  private ValidationPointers() {}

  /**
   * Runs {@code factory}, remapping any {@link JsonApiValidationException} pointer from {@code
   * coreRoot} onto {@code pointer.path()}.
   */
  static <T> T construct(JsonPointerAccumulator pointer, String coreRoot, Supplier<T> factory) {
    try {
      return factory.get();
    } catch (JsonApiValidationException ex) {
      throw relocate(ex, pointer, coreRoot);
    }
  }

  /**
   * Adapts open-value maps for public core constructors. Core declares type-use {@code @Nullable}
   * on map values; that annotation is not visible to NullAway across the published JAR boundary.
   */
  @SuppressWarnings({"NullAway"})
  static Map<String, Object> forCore(Map<String, @Nullable Object> map) {
    return map;
  }

  static JsonApiValidationException relocate(
      JsonApiValidationException ex, JsonPointerAccumulator pointer, String coreRoot) {
    String base = pointer.path();
    if (base.isEmpty() || coreRoot.isEmpty()) {
      return ex;
    }
    String corePtr = ex.jsonPointer();
    String relative;
    if (corePtr.equals(coreRoot)) {
      relative = "";
    } else if (corePtr.startsWith(coreRoot + "/")) {
      relative = corePtr.substring(coreRoot.length());
    } else {
      return new JsonApiValidationException(ex.ruleCode(), base, message(ex));
    }
    return new JsonApiValidationException(ex.ruleCode(), join(base, relative), message(ex));
  }

  /** Appends RFC 6901 relative segments onto an escaped reader base path. */
  static String join(String base, String relativePointer) {
    if (relativePointer.isEmpty()) {
      return base;
    }
    StringBuilder joined = new StringBuilder(base);
    for (String segment : decodeSegments(relativePointer)) {
      joined.append('/').append(PointerEscapes.escape(segment));
    }
    return joined.toString();
  }

  private static List<String> decodeSegments(String relativePointer) {
    List<String> segments = new ArrayList<>();
    if (!relativePointer.startsWith("/")) {
      segments.add(PointerEscapes.unescape(relativePointer));
      return segments;
    }
    int start = 1;
    for (int i = 1; i < relativePointer.length(); i++) {
      if (relativePointer.charAt(i) == '/') {
        segments.add(PointerEscapes.unescape(relativePointer.substring(start, i)));
        start = i + 1;
      }
    }
    segments.add(PointerEscapes.unescape(relativePointer.substring(start)));
    return segments;
  }

  private static String message(JsonApiValidationException ex) {
    String message = ex.getMessage();
    return message != null ? message : "Validation failed";
  }
}
