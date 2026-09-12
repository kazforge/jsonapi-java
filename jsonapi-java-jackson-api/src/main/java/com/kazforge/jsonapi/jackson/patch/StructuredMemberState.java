package com.kazforge.jsonapi.jackson.patch;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Sealed state algebra for one supplied nested {@link StructuredMember} of a {@link
 * StructuredPatch}.
 *
 * <p>The two permitted states ({@link Atomic} and {@link Structured}) are mutually exclusive and
 * constructor-validated, so contradictory states are unrepresentable.
 */
public sealed interface StructuredMemberState
    permits StructuredMemberState.Atomic, StructuredMemberState.Structured {

  /** A supplied atomic nested value. */
  record Atomic(@Nullable Object value) implements StructuredMemberState {

    /**
     * Creates an atomic supplied value. {@code null} is the null convention: the resulting
     * converted-null state (configured conversion may map a non-null wire value to null). It is not
     * proof that the wire token was JSON {@code null}; that distinction is not promised. It is
     * never omission.
     */
    public Atomic {
      value = PatchValues.freeze(value);
    }

    @Override
    public @Nullable Object value() {
      return PatchValues.expose(value);
    }
  }

  /** A supplied structured nested value; an empty list is a supplied empty structured object. */
  record Structured(List<StructuredMember> members) implements StructuredMemberState {

    public Structured {
      Objects.requireNonNull(members, "members");
      for (StructuredMember member : members) {
        Objects.requireNonNull(member, "members element");
      }
      members = List.copyOf(members);
    }
  }
}
