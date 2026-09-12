package com.kazforge.jsonapi.core.validation;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Local validation helper used by model compact constructors. */
public final class LocalValidation {

  private LocalValidation() {}

  public static void fail(ValidationRuleCode code, String path, String message) {
    throw new JsonApiValidationException(code, path, message);
  }

  /** Rejects a null required value with a stable rule code and JSON Pointer-like path. */
  public static <T> T requireNonNull(@Nullable T value, String path, String message) {
    if (value == null) {
      throw new JsonApiValidationException(ValidationRuleCode.NULL_REQUIRED_VALUE, path, message);
    }
    return value;
  }

  /**
   * Defensively copies a required collection, rejecting a null payload and null elements with
   * stable rule codes and indexed paths.
   */
  public static <T> List<T> copyRequiredList(
      @Nullable List<? extends @Nullable T> source, String path) {
    if (source == null) {
      throw new JsonApiValidationException(
          ValidationRuleCode.NULL_COLLECTION_PAYLOAD, path, "Collection payload must not be null");
    }
    List<T> copy = new ArrayList<>(source.size());
    for (int i = 0; i < source.size(); i++) {
      copy.add(requireNonNullElement(source.get(i), path + "/" + i));
    }
    return List.copyOf(copy);
  }

  private static <T> T requireNonNullElement(@Nullable T element, String path) {
    if (element == null) {
      throw new JsonApiValidationException(
          ValidationRuleCode.NULL_COLLECTION_ELEMENT, path, "Collection element must not be null");
    }
    return element;
  }
}
