package com.kazforge.jsonapi.jackson.diagnostic;

import java.io.Serial;
import java.io.Serializable;

/**
 * Backend-independent payload-safe source position for a document-read diagnostic. The current
 * Jackson adapters populate it from parser positions reported through caller-configured Jackson.
 *
 * <p>The value contains line, column, and character/byte offsets only, never source text. A
 * negative component is unavailable; {@link #UNKNOWN} has no available components.
 */
public record SourceLocation(int lineNumber, int columnNumber, long charOffset, long byteOffset)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Location when the current backend did not report a usable position. */
  public static final SourceLocation UNKNOWN = new SourceLocation(-1, -1, -1L, -1L);

  /** Returns whether at least one position component is available. */
  public boolean isKnown() {
    return lineNumber >= 0 || columnNumber >= 0 || charOffset >= 0 || byteOffset >= 0;
  }
}
