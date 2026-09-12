package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.jackson.diagnostic.SourceLocation;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.TokenStreamLocation;

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

  public static SourceLocation from(@Nullable TokenStreamLocation location) {
    if (location == null || TokenStreamLocation.NA.equals(location)) {
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

  /** Prefers an exception location and falls back to the parser cursor when it is usable. */
  public static SourceLocation fromOrCurrent(
      @Nullable TokenStreamLocation exceptionLocation, JsonParser parser) {
    SourceLocation exceptionSource = from(exceptionLocation);
    return exceptionSource.isKnown() ? exceptionSource : current(parser);
  }
}
