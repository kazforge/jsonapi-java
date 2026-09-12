package com.kazforge.jsonapi.jackson.representation;

import java.util.Objects;
import java.util.Set;

/**
 * Decides which relationships may be traversed for compound-document inclusion.
 *
 * <p>Policy governs inclusion traversal only. Linkage emission on selected resources remains
 * unaffected.
 */
public final class IncludePolicy {

  private enum Mode {
    DENY_ALL,
    ALLOW_ALL,
    ALLOWING
  }

  private static final IncludePolicy DENY_ALL = new IncludePolicy(Mode.DENY_ALL, Set.of());
  private static final IncludePolicy ALLOW_ALL = new IncludePolicy(Mode.ALLOW_ALL, Set.of());

  private final Mode mode;
  private final Set<RelationshipAllowance> allowances;

  private IncludePolicy(Mode mode, Set<RelationshipAllowance> allowances) {
    this.mode = mode;
    this.allowances = allowances;
  }

  /** Rejects every relationship for inclusion traversal. */
  public static IncludePolicy denyAll() {
    return DENY_ALL;
  }

  /** Permits every mapped relationship for inclusion traversal. */
  public static IncludePolicy allowAll() {
    return ALLOW_ALL;
  }

  /**
   * Permits only the given owner-type + relationship allowances. The allowance set is defensively
   * copied.
   */
  public static IncludePolicy allowing(Set<RelationshipAllowance> allowances) {
    Objects.requireNonNull(allowances, "allowances");
    return new IncludePolicy(Mode.ALLOWING, Set.copyOf(allowances));
  }

  /**
   * Returns whether inclusion traversal may follow {@code relationshipName} on a resource whose
   * JSON:API type is {@code ownerResourceType}.
   */
  public boolean allows(String ownerResourceType, String relationshipName) {
    Objects.requireNonNull(ownerResourceType, "ownerResourceType");
    Objects.requireNonNull(relationshipName, "relationshipName");
    return switch (mode) {
      case DENY_ALL -> false;
      case ALLOW_ALL -> true;
      case ALLOWING ->
          allowances.contains(RelationshipAllowance.of(ownerResourceType, relationshipName));
    };
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof IncludePolicy other
        && mode == other.mode
        && allowances.equals(other.allowances);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, allowances);
  }
}
