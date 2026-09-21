package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-owned identifier-meta enrichment for one wrapper occurrence.
 *
 * <p>The shared relationship writer invokes this callback after a {@code RelationshipLinkage}
 * occurrence's target has mapped to to-one linkage and the occurrence carries an identifier-meta
 * value; occurrences without a meta value are returned without enrichment and never consult it.
 * Each adapter keeps property-scoped conversion, object-shape validation, overlay state, and its
 * current stable diagnostics in its own write path until whole-meta extraction.
 *
 * <p>Tokens are deliberately opaque to the shared mapping domain. This is unsupported
 * implementation detail for backend cooperation, not consumer SPI, and must not appear in supported
 * backend signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
@FunctionalInterface
public interface RelationshipMetaEnricher<T> {

  /**
   * Overlays the occurrence's identifier meta onto {@code identifier}. {@code declaredMetaToken} is
   * the wrapper's declared identifier-meta token, {@code metaValue} the occurrence's present meta
   * value, {@code relationshipName} the relationship's JSON:API member name, and {@code
   * identifierMetaLocation} the occurrence's resource-relative identifier-meta location for backend
   * conversion diagnostics.
   */
  ResourceIdentifier enrichWrapperMeta(
      T declaredMetaToken,
      @Nullable Object metaValue,
      ResourceIdentifier identifier,
      String relationshipName,
      MappingLocation identifierMetaLocation);
}
