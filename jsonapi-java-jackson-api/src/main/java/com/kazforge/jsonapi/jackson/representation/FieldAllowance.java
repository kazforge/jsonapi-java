package com.kazforge.jsonapi.jackson.representation;

import java.util.Objects;

/**
 * Allowance key for field-policy checks: owner JSON:API resource type plus attribute or
 * relationship member name.
 *
 * <p>{@code resourceType} and {@code fieldName} are JSON:API names, not Java names. {@code
 * fieldName} is never {@code type}, {@code id}, or {@code lid}.
 */
public record FieldAllowance(String resourceType, String fieldName) {

  public FieldAllowance {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(fieldName, "fieldName");
  }

  public static FieldAllowance of(String resourceType, String fieldName) {
    return new FieldAllowance(resourceType, fieldName);
  }
}
