package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral declared shape of one mapped relationship property for advanced write
 * normalization.
 *
 * <p>The shape carries the declared to-one/to-many cardinality and the backend-native type tokens
 * the shared writer cannot introspect. An {@link Ordinary} relationship carries the ordinary
 * declared target token, which may be unresolvable and stays nullable; a {@link Wrapped}
 * relationship declares the opt-in {@code RelationshipLinkage<T, M>} wrapper with its
 * identifier-meta token plus the target's own recursively derived shape. Tokens are never resolved
 * or validated eagerly: the shared writer consults the ordinary target token only after
 * normalization selects the ordinary domain-object branch, and the wrapper meta token only inside
 * wrapper occurrence handling.
 *
 * <p>Tokens are deliberately opaque to the shared mapping domain: a backend may use Jackson types
 * or another native representation without leaking them into write semantics. This is unsupported
 * implementation detail for backend cooperation, not consumer SPI, and must not appear in supported
 * backend signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
public sealed interface RelationshipShape<T>
    permits RelationshipShape.Ordinary, RelationshipShape.Wrapped {

  /** Whether the relationship is declared to-many. */
  boolean toMany();

  /**
   * Declared ordinary target token; only consulted for non-wrapper relationships and stays {@code
   * null} when unresolvable.
   */
  default @Nullable T ordinaryTarget() {
    return null;
  }

  /** Declared wrapper identifier-meta token plus the target's own shape. */
  record Wrapped<T>(boolean toMany, @Nullable T meta, RelationshipShape<T> targetShape)
      implements RelationshipShape<T> {

    public Wrapped {
      Objects.requireNonNull(targetShape, "targetShape");
    }
  }

  /** Shape of an ordinary to-one or to-many relationship over one declared target token. */
  record Ordinary<T>(boolean toMany, @Nullable T declaredTarget) implements RelationshipShape<T> {

    @Override
    public @Nullable T ordinaryTarget() {
      return declaredTarget;
    }
  }

  /** Shape of an ordinary to-one or to-many relationship over one declared target token. */
  static <T> RelationshipShape<T> ordinary(boolean toMany, @Nullable T declaredTarget) {
    return new RelationshipShape.Ordinary<>(toMany, declaredTarget);
  }

  /**
   * Shape of a relationship declared with the opt-in {@code RelationshipLinkage} wrapper. The
   * nested {@code targetShape} is the target's own declared shape, derived by the backend, and is
   * applied when a wrapper occurrence's target is mapped.
   */
  static <T> RelationshipShape<T> wrapper(
      boolean toMany, @Nullable T declaredMeta, RelationshipShape<T> targetShape) {
    return new RelationshipShape.Wrapped<>(toMany, declaredMeta, targetShape);
  }
}
