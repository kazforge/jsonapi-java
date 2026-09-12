package com.kazforge.jsonapi.jackson.mapping;

import com.kazforge.jsonapi.core.model.ResourceIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Independently bound {@code included} resources of a domain document envelope.
 *
 * <p>{@link #resources()} preserves wire order. {@link #find(ResourceIdentity)} resolves a bound
 * DTO instance by structured identity; resources carrying both {@code id} and {@code lid} are
 * indexed under both {@link ResourceIdentity#ofId(String, String)} and {@link
 * ResourceIdentity#ofLid(String, String)} keys pointing to the same instance.
 *
 * <p>Instances are immutable: the wire-order list and the identity index are defensively copied at
 * construction, and mutating a construction-time source or a returned {@link #resources()} list
 * never changes wire order or {@link #find(ResourceIdentity)} results.
 *
 * <p>Assemble instances with {@link #of(List, List)}. The identity index is derived from the
 * identities declared for each wire-order position, so {@code find(identity)} can only return the
 * DTO at the position that declared that identity: inconsistent states are unrepresentable.
 * Duplicate identities across positions, length mismatches, and {@code null} elements are rejected
 * at construction. Major-specific readers build the declarations from validated documents, so wire
 * order and identity lookup always agree.
 */
public final class IncludedResources {

  private final List<Object> resources;
  private final Map<ResourceIdentity, Integer> identityIndex;

  private IncludedResources(List<Object> resources, Map<ResourceIdentity, Integer> identityIndex) {
    this.resources = resources;
    this.identityIndex = identityIndex;
  }

  /**
   * Creates an instance from wire-ordered bound DTOs and the identities declared for each
   * wire-order position.
   *
   * <p>{@code identitiesByPosition} must have exactly one entry per resource, in the same order.
   * The identity index is derived from the declarations, so every declared identity resolves to the
   * position that declared it and no identity can resolve to an undeclared position.
   *
   * <p>All arguments are defensively copied.
   *
   * @throws NullPointerException when an argument, a resource, an identity set, or an identity is
   *     {@code null}
   * @throws IllegalArgumentException when {@code identitiesByPosition} has a different size than
   *     {@code resources}, or the same identity is declared for more than one position
   */
  public static IncludedResources of(
      List<Object> resources, List<Set<ResourceIdentity>> identitiesByPosition) {
    List<Object> copiedResources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    List<Set<ResourceIdentity>> copiedIdentities =
        copyIdentities(Objects.requireNonNull(identitiesByPosition, "identitiesByPosition"));
    if (copiedIdentities.size() != copiedResources.size()) {
      throw new IllegalArgumentException(
          "Identities per position ("
              + copiedIdentities.size()
              + ") must match the resource count ("
              + copiedResources.size()
              + ")");
    }
    Map<ResourceIdentity, Integer> index = new LinkedHashMap<>();
    for (int position = 0; position < copiedIdentities.size(); position++) {
      for (ResourceIdentity identity : copiedIdentities.get(position)) {
        Integer previous = index.putIfAbsent(identity, position);
        if (previous != null) {
          throw new IllegalArgumentException(
              "Identity " + identity + " declared at positions " + previous + " and " + position);
        }
      }
    }
    return new IncludedResources(copiedResources, index);
  }

  private static List<Set<ResourceIdentity>> copyIdentities(
      List<Set<ResourceIdentity>> identitiesByPosition) {
    List<Set<ResourceIdentity>> copied = new ArrayList<>(identitiesByPosition.size());
    for (Set<ResourceIdentity> identities : identitiesByPosition) {
      copied.add(Set.copyOf(Objects.requireNonNull(identities, "identities at a position")));
    }
    return List.copyOf(copied);
  }

  /** Bound DTOs in wire order; unmodifiable. */
  public List<Object> resources() {
    return resources;
  }

  /** Returns the bound DTO indexed under the given identity, if any. */
  public Optional<Object> find(ResourceIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    Integer position = identityIndex.get(identity);
    return position == null ? Optional.empty() : Optional.of(resources.get(position));
  }
}
