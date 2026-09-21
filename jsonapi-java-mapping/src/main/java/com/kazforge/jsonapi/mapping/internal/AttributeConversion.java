package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of converting one selected attribute value through a {@link WriteResourceBackend}.
 *
 * <p>{@code emitted == false} covers backend-side omission, such as a suppressed property or an
 * absent Optional. Explicit JSON null stays distinct: {@code emitted == true} with a null {@code
 * value} is a present JSON null. The shared writer keeps that distinction when populating
 * attributes.
 */
@NullMarked
public record AttributeConversion(boolean emitted, @Nullable Object value) {

  public static AttributeConversion emitted(@Nullable Object value) {
    return new AttributeConversion(true, value);
  }

  public static AttributeConversion omitted() {
    return new AttributeConversion(false, null);
  }
}
