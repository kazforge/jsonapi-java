package com.kazforge.jsonapi.jackson.diagnostic;

import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Document read failure with a stable codec category, JSON Pointer-like path, and safe source
 * location. When construction or aggregate validation failed, {@link #ruleCode()} carries the core
 * {@link ValidationRuleCode}.
 */
public final class JsonApiDocumentReadException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final CodecFailureCategory category;
  private final String jsonPointer;
  private final SourceLocation sourceLocation;
  private final @Nullable ValidationRuleCode ruleCode;

  public JsonApiDocumentReadException(
      CodecFailureCategory category,
      String jsonPointer,
      SourceLocation sourceLocation,
      String message) {
    this(category, jsonPointer, sourceLocation, null, message, null);
  }

  public JsonApiDocumentReadException(
      CodecFailureCategory category,
      String jsonPointer,
      SourceLocation sourceLocation,
      @Nullable ValidationRuleCode ruleCode,
      String message) {
    this(category, jsonPointer, sourceLocation, ruleCode, message, null);
  }

  public JsonApiDocumentReadException(
      CodecFailureCategory category,
      String jsonPointer,
      SourceLocation sourceLocation,
      @Nullable ValidationRuleCode ruleCode,
      String message,
      @Nullable Throwable cause) {
    super(message, cause);
    this.category = Objects.requireNonNull(category, "category");
    this.jsonPointer = Objects.requireNonNull(jsonPointer, "jsonPointer");
    this.sourceLocation = Objects.requireNonNull(sourceLocation, "sourceLocation");
    this.ruleCode = ruleCode;
  }

  public CodecFailureCategory category() {
    return category;
  }

  public String jsonPointer() {
    return jsonPointer;
  }

  public SourceLocation sourceLocation() {
    return sourceLocation;
  }

  public @Nullable ValidationRuleCode ruleCode() {
    return ruleCode;
  }
}
