package com.kazforge.jsonapi.jackson.representation;

import java.util.Objects;
import java.util.Set;

/**
 * Application allow-list over {@code (resourceType, fieldName)} pairs for sparse fieldsets.
 *
 * <p>Policy is consulted only for names in a present fieldset entry. A present empty fieldset list
 * selects no attributes/relationships and does not consult per-field allows; non-field resource
 * members such as mapped resource meta remain independent (ADR-015).
 */
public final class FieldPolicy {

  private enum Mode {
    DENY_ALL,
    ALLOW_ALL,
    ALLOWING
  }

  private static final FieldPolicy DENY_ALL = new FieldPolicy(Mode.DENY_ALL, Set.of());
  private static final FieldPolicy ALLOW_ALL = new FieldPolicy(Mode.ALLOW_ALL, Set.of());

  private final Mode mode;
  private final Set<FieldAllowance> allowances;

  private FieldPolicy(Mode mode, Set<FieldAllowance> allowances) {
    this.mode = mode;
    this.allowances = allowances;
  }

  /** Rejects every fieldset field name. */
  public static FieldPolicy denyAll() {
    return DENY_ALL;
  }

  /** Permits every mapped fieldset field name. */
  public static FieldPolicy allowAll() {
    return ALLOW_ALL;
  }

  /**
   * Permits only the given owner-type + field allowances. The allowance set is defensively copied.
   */
  public static FieldPolicy allowing(Set<FieldAllowance> allowances) {
    Objects.requireNonNull(allowances, "allowances");
    return new FieldPolicy(Mode.ALLOWING, Set.copyOf(allowances));
  }

  /**
   * Returns whether a present fieldset may include {@code fieldName} on a resource whose JSON:API
   * type is {@code resourceType}.
   */
  public boolean allows(String resourceType, String fieldName) {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(fieldName, "fieldName");
    return switch (mode) {
      case DENY_ALL -> false;
      case ALLOW_ALL -> true;
      case ALLOWING -> allowances.contains(FieldAllowance.of(resourceType, fieldName));
    };
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof FieldPolicy other
        && mode == other.mode
        && allowances.equals(other.allowances);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, allowances);
  }
}
