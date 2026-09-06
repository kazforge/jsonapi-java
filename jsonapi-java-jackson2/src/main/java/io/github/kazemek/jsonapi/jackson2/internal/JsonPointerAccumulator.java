package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Accumulates a JSON Pointer-like path with RFC 6901 escaping ({@code ~} → {@code ~0}, {@code /} →
 * {@code ~1}) and records source locations into a caller-owned {@link ReadLocationIndex}.
 */
final class JsonPointerAccumulator {

  private final Deque<String> segments = new ArrayDeque<>();
  private final ReadLocationIndex locations;

  JsonPointerAccumulator(ReadLocationIndex locations) {
    this.locations = Objects.requireNonNull(locations, "locations");
  }

  void push(String segment) {
    segments.addLast(PointerEscapes.escape(segment));
  }

  void pushIndex(int index) {
    segments.addLast(Integer.toString(index));
  }

  void pop() {
    if (!segments.isEmpty()) {
      segments.removeLast();
    }
  }

  /** Records the current path using the start of the current token (first wins). */
  void capture(JsonParser parser) {
    locations.remember(path(), ReadLocations.token(parser));
  }

  String path() {
    if (segments.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (String segment : segments) {
      builder.append('/').append(segment);
    }
    return builder.toString();
  }
}
