package com.kazforge.jsonapi.jackson.patch;

import java.util.List;
import java.util.Objects;

/**
 * Jackson-major-neutral requested-change payload for a supplied structured value.
 *
 * <p>A present structured value's requested changes: exactly the supplied nested members, in the
 * application type's declaration order as Jackson resolves it. Omission is implied by absence — a
 * member that is not present in {@link #members()} was not supplied — mirroring the top-level
 * {@link PatchCommand#changes()} philosophy. An empty {@link #members()} list means the structured
 * value was supplied as an explicit empty object (present, zero nested changes); it is never a
 * clear-all or delete operation.
 *
 * <p>This type is the reusable neutral representation of the recursive structured value PATCH
 * semantics defined by ADR-014: it is a payload, not a {@link PatchChange} variant, so a structured
 * attribute on the low-level path surfaces as the existing {@link PatchChange.AttributeChange}
 * whose {@code value} is a {@code StructuredPatch} (discriminated via {@code instanceof}). A later
 * structured JSON:API {@code meta} mapping can consume the same payload without a second recursion
 * model. It is deliberately Jackson-major-neutral and Jackson-import-free.
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
