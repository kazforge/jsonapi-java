package com.kazforge.jsonapi.core.internal;

import org.jspecify.annotations.Nullable;

/** RFC 6901 JSON Pointer path helpers. These are document pointers, not filesystem paths. */
public final class JsonPointers {

  private JsonPointers() {}

  /** Returns {@code /segment} for an empty prefix, otherwise {@code prefix/segment}. */
  public static String child(@Nullable String prefix, String segment) {
    if (prefix == null || prefix.isEmpty()) {
      return root(segment);
    }
    return prefix + "/" + escapeSegment(segment); // NOSONAR java:S1075 - JSON Pointer delimiter
  }

  public static String root(String segment) {
    return "/"
        + escapeSegment(segment); // NOSONAR java:S1075 - JSON Pointer delimiter, not a file path
  }

  /** Escapes {@code ~} and {@code /} per RFC 6901 ({@code ~0}, {@code ~1}). */
  private static String escapeSegment(String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }
}
