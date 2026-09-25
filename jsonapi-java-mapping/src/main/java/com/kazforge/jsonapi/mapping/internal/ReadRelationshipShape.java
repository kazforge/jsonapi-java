package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral declared shape of one mapped relationship property for shared
 * relationship-linkage binding.
 *
 * <p>The shape carries the declared to-one/to-many cardinality plus the backend-native tokens the
 * shared reader cannot introspect. A {@link Direct} relationship targets the built-in {@link
 * com.kazforge.jsonapi.core.model.ResourceIdentifier} identity and needs no configured mapper. A
 * {@link Mapped} relationship carries the opaque mapping token a configured linkage mapper expects.
 * A {@link Wrapped} relationship declares the opt-in {@code RelationshipLinkage<T, M>} wrapper with
 * its opaque identifier-meta token and the target's own {@link Direct}/{@link Mapped} shape.
 *
 * <p>Tokens are deliberately opaque to the shared mapping domain: a backend may use Jackson types
 * or another native representation without leaking them into read semantics. This is unsupported
 * implementation detail for backend cooperation, not consumer SPI, and must not appear in supported
 * backend signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
public sealed interface ReadRelationshipShape<T>
    permits ReadRelationshipShape.Direct,
        ReadRelationshipShape.Mapped,
        ReadRelationshipShape.Wrapped {

  /** Whether the declared relationship property is to-many. */
  boolean toMany();

  /**
   * Built-in {@link com.kazforge.jsonapi.core.model.ResourceIdentifier} target bound without a
   * configured mapper. The shared reader owns the identifier copy, cardinality check, and
   * null/empty short-circuit for this branch.
   */
  record Direct<T>(boolean toMany) implements ReadRelationshipShape<T> {}

  /**
   * Configured mapper target. {@code target} is the opaque mapping token the backend's linkage
   * mapper invocation expects; the shared reader passes it back through the backend for non-empty,
   * valid linkage only.
   */
  record Mapped<T>(boolean toMany, T target) implements ReadRelationshipShape<T> {

    public Mapped {
      Objects.requireNonNull(target, "target");
    }
  }

  /**
   * Opt-in {@code RelationshipLinkage} wrapper: the opaque identifier-meta token plus the target's
   * own {@link Direct}/{@link Mapped} shape. The nested shape is always a to-one target shape
   * because wrapper occurrences pair one target with one occurrence's identifier meta.
   */
  record Wrapped<T>(boolean toMany, T meta, ReadRelationshipShape<T> targetShape)
      implements ReadRelationshipShape<T> {

    public Wrapped {
      Objects.requireNonNull(meta, "meta");
      Objects.requireNonNull(targetShape, "targetShape");
    }
  }
}
