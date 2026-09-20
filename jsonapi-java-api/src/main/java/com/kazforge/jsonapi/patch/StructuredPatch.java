package com.kazforge.jsonapi.patch;

import java.util.List;
import java.util.Objects;

/**
 * Backend-independent low-level requested-change payload for a supplied structured value. The
 * current Jackson adapters populate it through caller-configured Jackson.
 *
 * <p>A present structured value's requested changes: exactly the supplied nested members, in the
 * application type's declaration order as the configured Jackson runtime resolves it. Omission is
 * implied by absence — a member that is not present in {@link #members()} was not supplied —
 * mirroring the top-level {@link PatchCommand#changes()} philosophy. An empty {@link #members()}
 * list means the structured value was supplied as an explicit empty object (present, zero nested
 * changes); it is never a clear-all or delete operation.
 *
 * <p>This is a payload, not a {@link PatchChange} variant. A recursively traversed attribute
 * appears as an {@link PatchChange.AttributeChange} whose value is a {@code StructuredPatch};
 * recursively traversed resource or relationship meta uses the corresponding meta change variant
 * with the same payload. Consumers discriminate it from an atomic replacement with {@code
 * instanceof} and decide how to apply the requested nested changes.
 *
 * <p>Immutability: {@code StructuredPatch} and its members freeze recursively at construction;
 * atomic container values use the same shallow-freeze convention as {@link PatchChange}.
 */
public record StructuredPatch(List<StructuredMember> members) {

  public StructuredPatch {
    Objects.requireNonNull(members, "members");
    for (StructuredMember member : members) {
      Objects.requireNonNull(member, "members element");
    }
    members = List.copyOf(members);
  }
}
