package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Closed backend-neutral normalization of one mapped relationship value.
 *
 * <p>The backend classifies its native value model into exactly one of three states: an ordinary
 * to-one target, an ordinary to-many target list, or a prebuilt {@link RelationshipData} for an
 * excluded direct or wrapper form. Cardinality is therefore explicit rather than inferred again by
 * the shared writer, and advanced forms keep their adapter-owned linkage and diagnostics instead of
 * becoming a second writer.
 *
 * <p>Ordinary targets are application values; the shared writer extracts their identity through
 * {@link WriteResourceBackend} and builds single or collection linkage itself. A null target in
 * {@link ToOne} is present-null linkage, and an empty {@link ToMany} list is present-empty linkage.
 * This is unsupported implementation detail for backend cooperation, not consumer SPI.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
@SuppressWarnings("unused")
public sealed interface RelationshipValue<T>
    permits RelationshipValue.ToOne, RelationshipValue.ToMany, RelationshipValue.Linkage {

  /** An ordinary domain target, or null for the absent/empty to-one state. */
  record ToOne<T>(@Nullable Object target, @Nullable T declaredTargetType)
      implements RelationshipValue<T> {

    public ToOne {
      if (target != null && declaredTargetType == null) {
        throw new IllegalArgumentException("a present target requires its declared type");
      }
    }
  }

  /**
   * Ordinary domain targets, in value order, with the declared target type used for effective-type
   * resolution. The type token is absent only for the empty state, where no target is read.
   */
  record ToMany<T>(List<Object> targets, @Nullable T declaredTargetType)
      implements RelationshipValue<T> {

    public ToMany {
      Objects.requireNonNull(targets, "targets");
      targets = List.copyOf(targets);
      if (!targets.isEmpty() && declaredTargetType == null) {
        throw new IllegalArgumentException("non-empty targets require a declared target type");
      }
    }
  }

  /** Adapter-owned linkage for a direct or wrapper form the shared writer must not reinterpret. */
  record Linkage<T>(RelationshipData data) implements RelationshipValue<T> {

    public Linkage {
      Objects.requireNonNull(data, "data");
    }
  }
}
