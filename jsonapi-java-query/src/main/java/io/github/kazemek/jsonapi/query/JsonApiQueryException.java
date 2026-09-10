package io.github.kazemek.jsonapi.query;

import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when a decoded or raw query cannot be represented by the query contract.
 *
 * <p>The exception carries a stable diagnostic and the decoded parameter name when one was
 * available. It does not select an HTTP response or otherwise prescribe transport behavior.
 */
public final class JsonApiQueryException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final QueryDiagnostic diagnostic;
  private final @Nullable String parameterName;

  public JsonApiQueryException(
      QueryDiagnostic diagnostic, @Nullable String parameterName, String message) {
    this(diagnostic, parameterName, message, null);
  }

  public JsonApiQueryException(
      QueryDiagnostic diagnostic,
      @Nullable String parameterName,
      String message,
      @Nullable Throwable cause) {
    super(message, cause);
    this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    this.parameterName = parameterName;
  }

  public QueryDiagnostic diagnostic() {
    return diagnostic;
  }

  /** Returns the decoded parameter name, or {@code null} before a name was decoded. */
  public @Nullable String parameterName() {
    return parameterName;
  }
}
