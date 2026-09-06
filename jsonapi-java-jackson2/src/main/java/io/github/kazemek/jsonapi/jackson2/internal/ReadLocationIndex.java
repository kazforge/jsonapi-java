package io.github.kazemek.jsonapi.jackson2.internal;

import io.github.kazemek.jsonapi.jackson.diagnostic.SourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Retains the first captured {@link SourceLocation} for each JSON Pointer-like path so aggregate
 * (and post-parse local) failures can resolve the exact or nearest enclosing token location.
 */
public final class ReadLocationIndex {

  private final Map<String, SourceLocation> locations = new LinkedHashMap<>();

  public ReadLocationIndex() {
    // LinkedHashMap field initializer is sufficient; no further setup.
  }

  public void remember(String path, SourceLocation location) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(location, "location");
    locations.putIfAbsent(path, location);
  }

  /**
   * Returns the location for {@code jsonPointer}, or the nearest recorded ancestor, or {@link
   * SourceLocation#UNKNOWN}.
   */
  public SourceLocation resolve(String jsonPointer) {
    Objects.requireNonNull(jsonPointer, "jsonPointer");
    String path = jsonPointer;
    while (true) {
      SourceLocation location = locations.get(path);
      if (location != null) {
        return location;
      }
      if (path.isEmpty()) {
        return SourceLocation.UNKNOWN;
      }
      int slash = path.lastIndexOf('/');
      path = slash <= 0 ? "" : path.substring(0, slash);
    }
  }
}
