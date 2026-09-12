package com.kazforge.jsonapi.jackson.document;

import com.kazforge.jsonapi.core.validation.ValidationContext;
import java.util.Objects;

/**
 * Immutable read policy: aggregate {@link ValidationContext} plus explicit {@link PrimaryDataKind}.
 */
public record DocumentReadContext(
    ValidationContext validationContext, PrimaryDataKind primaryDataKind) {

  public DocumentReadContext {
    Objects.requireNonNull(validationContext, "validationContext");
    Objects.requireNonNull(primaryDataKind, "primaryDataKind");
  }

  /** Resource primary-data kind with {@link ValidationContext#defaults()}. */
  public static DocumentReadContext resourceDefaults() {
    return new DocumentReadContext(ValidationContext.defaults(), PrimaryDataKind.RESOURCE);
  }

  /** Identifier primary-data kind with {@link ValidationContext#defaults()}. */
  public static DocumentReadContext identifierDefaults() {
    return new DocumentReadContext(
        ValidationContext.defaults(), PrimaryDataKind.RESOURCE_IDENTIFIER);
  }

  public static DocumentReadContext of(
      ValidationContext validationContext, PrimaryDataKind primaryDataKind) {
    return new DocumentReadContext(validationContext, primaryDataKind);
  }

  public DocumentReadContext withValidationContext(ValidationContext validationContext) {
    return new DocumentReadContext(validationContext, primaryDataKind);
  }

  public DocumentReadContext withPrimaryDataKind(PrimaryDataKind primaryDataKind) {
    return new DocumentReadContext(validationContext, primaryDataKind);
  }
}
