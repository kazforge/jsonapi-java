package com.kazforge.jsonapi.core.validation;

import java.io.Serial;

/** Validation failure carrying a stable rule code and JSON Pointer-like path. */
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

  public ValidationRuleCode ruleCode() {
    return ruleCode;
  }

  public String jsonPointer() {
    return jsonPointer;
  }
}
