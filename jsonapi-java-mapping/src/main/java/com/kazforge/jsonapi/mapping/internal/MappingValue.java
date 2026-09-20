package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.Nullable;

/** Result of backend-controlled conversion of one mapped attribute value. */
public record MappingValue(boolean emitted, @Nullable Object value) {

  public static MappingValue emitted(@Nullable Object value) {
    return new MappingValue(true, value);
  }

  public static MappingValue omitted() {
    return new MappingValue(false, null);
  }
}
