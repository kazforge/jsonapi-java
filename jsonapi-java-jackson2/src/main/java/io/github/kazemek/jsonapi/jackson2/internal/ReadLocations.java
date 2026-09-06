package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import io.github.kazemek.jsonapi.jackson.diagnostic.SourceLocation;
import org.jspecify.annotations.Nullable;

/** Converts Jackson locations into safe {@link SourceLocation} values. */
public final class ReadLocations {

  private ReadLocations() {}

  /** Current input cursor — use for EOF or when no token is present. */
  public static SourceLocation current(JsonParser parser) {
    return from(parser.currentLocation());
  }

  /** Start location of the current token — use when a token is present. */
  public static SourceLocation token(JsonParser parser) {
    return from(parser.currentTokenLocation());
  }

  public static SourceLocation from(@Nullable JsonLocation location) {
    if (location == null || JsonLocation.NA.equals(location)) {
      return SourceLocation.UNKNOWN;
    }
    SourceLocation source =
        new SourceLocation(
            location.getLineNr(),
            location.getColumnNr(),
            location.getCharOffset(),
            location.getByteOffset());
    return source.isKnown() ? source : SourceLocation.UNKNOWN;
  }
}
