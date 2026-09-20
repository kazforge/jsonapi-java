package com.kazforge.jsonapi.diagnostic;

import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Failure while decoding or validating a JSON:API document.
 *
 * <p>Adapter-produced instances carry a stable {@link CodecFailureCategory}, a document-relative
 * RFC 6901 JSON Pointer ({@code ""} for the document root), and a payload-safe best-effort {@link
 * SourceLocation}. For {@link CodecFailureCategory#LOCAL_VALIDATION} and {@link
 * CodecFailureCategory#AGGREGATE_VALIDATION}, {@link #ruleCode()} carries the core {@link
 * ValidationRuleCode}; codec failures do not invent one.
 *
 * <p>This family ends at validated core-document construction. Domain binding, resource mapping,
 * registry, and representation failures use {@link JsonApiMappingException} instead.
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

  /** Decoding or validation stage that failed. */
  public CodecFailureCategory category() {
    return category;
  }

  /** Document-relative JSON Pointer; {@code ""} identifies the document root. */
  public String jsonPointer() {
    return jsonPointer;
  }

  /** Best-effort payload-safe source position, or {@link SourceLocation#UNKNOWN}. */
  public SourceLocation sourceLocation() {
    return sourceLocation;
  }

  /** Core validation rule when one was reported, otherwise {@code null}. */
  public @Nullable ValidationRuleCode ruleCode() {
    return ruleCode;
  }
}
