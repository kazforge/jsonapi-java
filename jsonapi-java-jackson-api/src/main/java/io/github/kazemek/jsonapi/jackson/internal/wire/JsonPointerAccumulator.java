package io.github.kazemek.jsonapi.jackson.internal.wire;

import io.github.kazemek.jsonapi.jackson.diagnostic.SourceLocation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Accumulates a JSON Pointer-like path with RFC 6901 escaping and records already-converted neutral
 * source locations into a caller-owned {@link ReadLocationIndex}.
 */
public final class JsonPointerAccumulator {

  private final Deque<String> segments = new ArrayDeque<>();
  private final ReadLocationIndex locations;

  public JsonPointerAccumulator(ReadLocationIndex locations) {
    this.locations = Objects.requireNonNull(locations, "locations");
  }

  public void push(String segment) {
    segments.addLast(PointerEscapes.escape(segment));
  }

  public void pushIndex(int index) {
    segments.addLast(Integer.toString(index));
  }

  public void pop() {
    if (!segments.isEmpty()) {
      segments.removeLast();
    }
  }

  /** Records the current path using the supplied neutral location (first capture wins). */
  public void capture(SourceLocation location) {
    locations.remember(path(), Objects.requireNonNull(location, "location"));
  }

  public String path() {
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
