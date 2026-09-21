package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Relationships;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-owned relationship phase of one basic resource write.
 *
 * <p>The shared writer owns fieldset filtering and supplies the selected relationship properties in
 * declaration order; the backend builds the relationship members, keeping the advanced direct and
 * wrapper forms, their identifier meta, and per-relationship meta in its own write orchestration
 * for later extraction. The returned relationships are assembled into the resource object by the
 * shared writer, including empty-member omission.
 *
 * <p>This is unsupported implementation detail for backend cooperation, not consumer SPI, and must
 * not appear in supported backend signatures.
 *
 * @param <T> opaque backend-native type token
 * @param <P> opaque backend-native property token
 */
@NullMarked
@FunctionalInterface
public interface BasicRelationshipWriter<T, P> {

  /**
   * Builds the relationship members for the selected properties, in the supplied order. An empty
   * selection yields {@link Relationships#empty()}.
   */
  Relationships buildRelationships(
      Object resource, T declaredType, List<WriteProperty<P>> selectedRelationships);
}
