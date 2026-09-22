package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of converting one selected member value through a {@link WriteResourceBackend}.
 *
 * <p>Shared by attribute, resource-meta, relationship-meta, and identifier-meta conversion so the
 * omitted-versus-emitted distinction cannot drift between them. {@code emitted == false} covers
 * backend-side omission, such as a suppressed property, an absent {@link java.util.Optional}, or a
 * configured serializer that writes nothing. Explicit JSON null stays distinct: {@code emitted ==
 * true} with a null {@code value} is a present JSON null, which callers interpret by member
 * location.
 */
@NullMarked
public record MemberConversion(boolean emitted, @Nullable Object value) {

  public static MemberConversion emitted(@Nullable Object value) {
    return new MemberConversion(true, value);
  }

  public static MemberConversion omitted() {
    return new MemberConversion(false, null);
  }
}
