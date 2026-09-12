package com.kazforge.jsonapi.fixtures.domainpatch;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ordinary structured domain value type with {@code Set}/{@code array}/{@code Map} members, proving
 * the low-level path treats each as one atomic replacement boundary rather than recursing into
 * elements or map keys (ADR-014).
 */
public record AddressWithContainers(
    String street, Set<String> aliases, String[] initials, Map<String, Integer> scores) {

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj
        instanceof
        AddressWithContainers(
            String otherStreet,
            Set<String> otherAliases,
            String[] otherInitials,
            Map<String, Integer> otherScores)) {
      return Objects.equals(street, otherStreet)
          && Objects.equals(aliases, otherAliases)
          && Arrays.equals(initials, otherInitials)
          && Objects.equals(scores, otherScores);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(street, aliases, Arrays.hashCode(initials), scores);
  }

  @Override
  public String toString() {
    return "AddressWithContainers[street="
        + street
        + ", aliases="
        + aliases
        + ", initials="
        + Arrays.toString(initials)
        + ", scores="
        + scores
        + "]";
  }
}
