package com.kazforge.jsonapi.jackson.patch;

import org.jspecify.annotations.Nullable;

/**
 * Jackson-major-neutral tri-state presence for a single patchable PATCH DTO member.
 *
 * <p>A patchable attribute or relationship declared as {@code PatchPresence<T>} on an annotated
 * PATCH DTO distinguishes three states: the member was omitted from the update document ({@link
 * Omitted}), the member was supplied with a non-null value ({@link Present#value() value}), or the
 * member was supplied with an explicit JSON {@code null} / null relationship linkage ({@link
 * Present} with a {@code null} value). {@code Present} with a {@code null} value is present — it is
 * not omission.
 *
 * <p>The type is deliberately Jackson-major-neutral and models only presence. It does not own
 * mapping, validation, persistence, or business semantics; applications inspect the tri-state and
 * decide how to apply each member. Because nullable {@link java.util.Optional} is a distinct
 * concern, {@code PatchPresence<Optional<T>>} remains meaningful: an omitted member is {@link
 * Omitted}, while a supplied {@code null} becomes {@code Present(Optional.empty())} when that is
 * the inner type's normal conversion result.
 *
 * <p>Consume the tri-state with exhaustive pattern matching over {@link Omitted} and {@link
 * Present}, or use the {@link #isOmitted()} convenience.
 */
// The type parameter is the payload type of the public PATCH DTO contract; consumers declare
// PatchPresence<T> even though the interface body itself does not reference T.
@SuppressWarnings({"java:S2326", "unused"})
public sealed interface PatchPresence<T> permits PatchPresence.Omitted, PatchPresence.Present {

  /** The member was omitted from the update document; no requested change. */
  record Omitted<T>() implements PatchPresence<T> {}

  /**
   * The member was supplied. A {@code null} {@link #value()} means explicit JSON {@code null} (or
   * null relationship linkage), never omission; the component stays {@code @Nullable} so {@code
   * present(null)} is representable.
   */
  record Present<T>(@Nullable T value) implements PatchPresence<T> {}

  /** Returns {@code true} when this state is {@link Omitted}. */
  default boolean isOmitted() {
    return this instanceof PatchPresence.Omitted;
  }

  /** Returns an {@link Omitted} instance for {@code T}. */
  static <T> PatchPresence<T> omitted() {
    return new PatchPresence.Omitted<>();
  }

  /** Returns a {@link Present} instance for {@code T}; {@code value} may be {@code null}. */
  static <T> PatchPresence<T> present(@Nullable T value) {
    return new PatchPresence.Present<>(value);
  }
}
