package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-owned target resolution for one ordinary relationship domain-object branch.
 *
 * <p>The shared relationship writer invokes this callback only after normalization selects the
 * ordinary domain-object branch; null/empty, direct-identifier, and direct-data branches never
 * consult it. The declared target token stays opaque: it comes from the {@link RelationshipShape}
 * the backend supplied, and resolution keeps native type specialization, unresolved-target
 * validation, and native diagnostics in the backend's own write path.
 *
 * <p>This is unsupported implementation detail for backend cooperation, not consumer SPI, and must
 * not appear in supported backend signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
@FunctionalInterface
public interface RelationshipTargetResolver<T> {

  /**
   * Resolves the effective target token used to construct ordinary linkage for {@code target}.
   * {@code target} is one representative domain object of the ordinary branch and may be absent for
   * a to-many branch that carries no resolvable elements; {@code declaredTargetToken} is the
   * relationship's declared ordinary target token and may be unresolvable ({@code null}); {@code
   * relationshipLocation} is the relationship's resource-relative {@code data} location for backend
   * resolution diagnostics.
   */
  T resolveTarget(
      @Nullable Object target,
      @Nullable T declaredTargetToken,
      MappingLocation relationshipLocation);
}
