package com.kazforge.jsonapi.jackson.mapping;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in application-owned wrapper for one relationship linkage occurrence that carries identifier
 * meta.
 *
 * <p>{@link #target()} is mapped exactly as the corresponding ordinary relationship target would
 * be. {@link #meta()} maps exclusively to that occurrence's JSON:API {@code
 * ResourceIdentifier.meta}; it is not relationship-level {@code meta} ({@code Relationship.meta})
 * and is not resource-level {@code meta} ({@code ResourceObject.meta}).
 *
 * <p>The wrapper is optional. Ordinary {@code @JsonApiRelationship} shapes — a single target,
 * {@code List}/{@code Set}/array/{@code Optional} of targets, a direct {@code ResourceIdentifier},
 * or a custom linkage mapping — remain valid when identifier meta is not required. Applications
 * that need per-linkage identifier meta declare the richer value:
 *
 * <pre>{@code
 * @JsonApiRelationship
 * RelationshipLinkage<Person, AuthorMeta> author;
 *
 * @JsonApiRelationship
 * List<RelationshipLinkage<Comment, CommentMeta>> comments;
 * }</pre>
 *
 * <p>{@code meta == null} supplies no identifier meta: the target is mapped normally, and any
 * {@code ResourceIdentifier.meta} already present on a direct identifier target is left in place.
 * The wrapper itself is not a JSON:API resource and is not independently patchable; identifier meta
 * participates in presence-aware PATCH only as part of whole-linkage replacement (ADR-017).
 *
 * @param <T> the ordinary relationship target type
 * @param <M> the application-owned identifier-meta type
 */
public record RelationshipLinkage<T, M>(T target, @Nullable M meta) {

  public RelationshipLinkage {
    Objects.requireNonNull(target, "target");
  }
}
