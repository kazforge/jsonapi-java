package com.kazforge.jsonapi.core.validation;

import java.io.Serial;

/**
 * Validation failure carrying a stable rule code and JSON Pointer-like path.
 *
 * <p>Consumers should branch on {@link #ruleCode()} and use {@link #jsonPointer()} to locate the
 * failure. Exception messages are explanatory text, not a stable machine-readable contract.
 */
public final class JsonApiValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ValidationRuleCode ruleCode;
  private final String jsonPointer;

  public JsonApiValidationException(
      ValidationRuleCode ruleCode, String jsonPointer, String message) {
    super(message);
    this.ruleCode = ruleCode;
    this.jsonPointer = jsonPointer;
  }

  /** Returns the stable machine-readable validation rule. */
  public ValidationRuleCode ruleCode() {
    return ruleCode;
  }

  /** Returns the failing member path; an empty string denotes the document root. */
  public String jsonPointer() {
    return jsonPointer;
  }
}
