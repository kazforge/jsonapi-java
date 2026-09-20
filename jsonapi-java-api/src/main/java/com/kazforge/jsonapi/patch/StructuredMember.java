package com.kazforge.jsonapi.patch;

import java.util.Objects;

/**
 * Backend-independent supplied nested member of a {@link StructuredPatch}. The current Jackson
 * adapters populate it through caller-configured Jackson.
 *
 * <p>Both names are carried because they serve different purposes: {@link #wireName()} is the final
 * JSON:API document member name (used for wire lookup and diagnostics), and {@link #logicalName()}
 * is the logical application-property identity on the application type (used for
 * application-property correspondence). Configured Jackson performs the translation between the
 * logical identity and the final member name via its external property name; there is deliberately
 * no single ambiguous {@code name} field and no third external-name field.
 */
public record StructuredMember(String wireName, String logicalName, StructuredMemberState state) {

  public StructuredMember {
    Objects.requireNonNull(wireName, "wireName");
    Objects.requireNonNull(logicalName, "logicalName");
    Objects.requireNonNull(state, "state");
  }
}
