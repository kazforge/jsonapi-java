package com.kazforge.jsonapi.document;

import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import java.util.Objects;

/**
 * Immutable document-read contract with independent validation and decoding axes.
 *
 * <p>{@link #validationContext()} controls aggregate validation, including document usage,
 * primary-data endpoint role, extension/profile policy, and endpoint identity. {@link
 * #primaryDataKind()} independently controls whether primary-data objects and arrays decode as
 * resources or resource identifiers. Changing either component does not infer or rewrite the other.
 *
 * <p>In particular, {@link #identifierDefaults()} selects identifier decoding with {@link
 * ValidationContext#defaults()}; it does not select relationship-endpoint validation. Callers
 * reading relationship linkage must also supply a validation context whose primary-data context is
 * {@link com.kazforge.jsonapi.core.validation.PrimaryDataContext#RELATIONSHIP}.
 */
public record DocumentReadContext(
    ValidationContext validationContext, PrimaryDataKind primaryDataKind) {

  public DocumentReadContext {
    Objects.requireNonNull(validationContext, "validationContext");
    Objects.requireNonNull(primaryDataKind, "primaryDataKind");
  }

  /** Resource decoding with {@link ValidationContext#defaults()}. */
  public static DocumentReadContext resourceDefaults() {
    return new DocumentReadContext(ValidationContext.defaults(), PrimaryDataKind.RESOURCE);
  }

  /**
   * Identifier decoding with {@link ValidationContext#defaults()}; the endpoint role stays
   * resource.
   */
  public static DocumentReadContext identifierDefaults() {
    return new DocumentReadContext(
        ValidationContext.defaults(), PrimaryDataKind.RESOURCE_IDENTIFIER);
  }

  public static DocumentReadContext of(
      ValidationContext validationContext, PrimaryDataKind primaryDataKind) {
    return new DocumentReadContext(validationContext, primaryDataKind);
  }

  /** Returns a copy with the given validation policy and the same primary-data decoding kind. */
  public DocumentReadContext withValidationContext(ValidationContext validationContext) {
    return new DocumentReadContext(validationContext, primaryDataKind);
  }

  /** Returns a copy with the given primary-data decoding kind and the same validation policy. */
  public DocumentReadContext withPrimaryDataKind(PrimaryDataKind primaryDataKind) {
    return new DocumentReadContext(validationContext, primaryDataKind);
  }
}
