package com.kazforge.jsonapi.jackson.mapping;

import com.kazforge.jsonapi.core.model.Links;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Additive relationship-level decoration for domain writes.
 *
 * <p>Carries only {@code Relationship.links} for an already-mapped {@code @JsonApiRelationship}. It
 * does not replace linkage, meta, or additional members, and it never creates a relationship that
 * normal mapping did not produce.
 */
public record RelationshipDecoration(@Nullable Links links) {

  /**
   * Creates a decoration with the given links. {@code links} must not be {@code null}; use {@link
   * #empty()} when no linkage-agnostic decoration is needed.
   */
  public static RelationshipDecoration of(Links links) {
    return new RelationshipDecoration(Objects.requireNonNull(links, "links"));
  }

  /** Alias for {@link #of(Links)} that reads fluently when decorating relationships. */
  public static RelationshipDecoration links(Links links) {
    return of(links);
  }

  /** Returns an empty relationship decoration (no links). */
  public static RelationshipDecoration empty() {
    return new RelationshipDecoration(null);
  }

  /**
   * Returns {@code true} when this decoration carries no links. A present-empty {@link Links}
   * ({@code Links.empty()} / {@code "links":{}}) is a wire-visible value and is not empty.
   */
  public boolean isEmpty() {
    return links == null;
  }
}
