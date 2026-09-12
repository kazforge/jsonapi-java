package com.kazforge.jsonapi.jackson.patch;

import java.util.Objects;

/**
 * One supplied nested member of a {@link StructuredPatch}.
 *
 * <p>Both names are carried because they serve different purposes: {@link #wireName()} is the
 * document member name (used for wire lookup and diagnostics), and {@link #logicalName()} is the
 * Jackson property name on the application type (used for application-property correspondence). The
 * naming-strategy translation between them is documented in ADR-014; there is deliberately no
 * single ambiguous {@code name} field.
 */
public record StructuredMember(String wireName, String logicalName, StructuredMemberState state) {

  public StructuredMember {
    Objects.requireNonNull(wireName, "wireName");
    Objects.requireNonNull(logicalName, "logicalName");
    Objects.requireNonNull(state, "state");
  }
}
