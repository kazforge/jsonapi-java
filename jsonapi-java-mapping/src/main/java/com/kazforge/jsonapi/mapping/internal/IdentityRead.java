package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of reading one mapped identity role through a {@link WriteResourceBackend}.
 *
 * <p>{@code present == false} means the role or its value is absent, so the enclosing strict/create
 * identity rule decides whether that is a failure. {@code present == true} with a null {@code
 * value} means a present value could not be converted; the shared writer reports that with the
 * backend's stable missing-identifier diagnostic at the role's own wire location.
 */
@NullMarked
public record IdentityRead(boolean present, @Nullable String value) {

  public IdentityRead {
    if (value != null && !present) {
      throw new IllegalArgumentException("an absent read cannot carry a value");
    }
  }

  public static IdentityRead absent() {
    return new IdentityRead(false, null);
  }

  public static IdentityRead of(@Nullable String value) {
    return new IdentityRead(true, value);
  }
}
