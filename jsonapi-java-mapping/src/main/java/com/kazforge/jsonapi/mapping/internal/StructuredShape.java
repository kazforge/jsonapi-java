package com.kazforge.jsonapi.mapping.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral resolved structured shape of one adapter-native type token: the visible, bindable
 * members that participate in structured PATCH traversal.
 *
 * <p>Each member carries only native facts discovered by the adapter's own introspection: the
 * internal and wire names, the member's declared type token, whether the declared type is exactly
 * {@code PatchPresence<T>}, whether it is a presence-aware attempt ({@code PatchPresence}, {@code
 * PatchPresence.Present}, or {@code PatchPresence.Omitted}), and whether wrapper-level
 * serialization/deserialization customization is present. The shared typed and low-level traversal
 * policy is derived from those facts by {@link StructuredPatchBinder}; the adapter never decides
 * atomic-versus-recursive behavior.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
public record StructuredShape<T>(List<Member<T>> members) {

  public StructuredShape {
    Objects.requireNonNull(members, "members");
    members = List.copyOf(members);
  }

  /** Returns the member whose wire name is {@code name}, or {@code null} when none matches. */
  public @Nullable Member<T> memberByWire(String name) {
    for (Member<T> member : members) {
      if (member.wireName().equals(name)) {
        return member;
      }
    }
    return null;
  }

  /**
   * True when every visible member is declared exactly as {@code PatchPresence<T>} (at least one
   * presence attempt, none other), so the shape is a deliberately presence-aware nested PATCH
   * shape.
   */
  public boolean presenceAware() {
    return !members.isEmpty() && hasPresenceAttempt() && allExactlyPresence();
  }

  /**
   * True when at least one member is a presence-aware attempt but not every member is exactly
   * {@code PatchPresence<T>}; such a shape is invalid when used as a nested typed PATCH shape.
   */
  public boolean mixed() {
    return hasPresenceAttempt() && !allExactlyPresence();
  }

  private boolean hasPresenceAttempt() {
    for (Member<T> member : members) {
      if (member.presenceAttempt()) {
        return true;
      }
    }
    return false;
  }

  private boolean allExactlyPresence() {
    for (Member<T> member : members) {
      if (!member.patchPresence()) {
        return false;
      }
    }
    return true;
  }

  /**
   * One visible, bindable member of a structured shape with its native facts.
   *
   * @param <T> opaque backend-native type token
   */
  @NullMarked
  public record Member<T>(
      String internalName,
      String wireName,
      T declaredType,
      boolean patchPresence,
      boolean presenceAttempt,
      boolean wrapperCustomization,
      boolean deserializationCustomization) {

    public Member {
      Objects.requireNonNull(internalName, "internalName");
      Objects.requireNonNull(wireName, "wireName");
      Objects.requireNonNull(declaredType, "declaredType");
    }
  }
}
