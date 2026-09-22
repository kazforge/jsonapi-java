package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared basic resource reader.
 *
 * <p>Only native mechanics live here: the raw diagnostic class of a mapped property, configured
 * wire-identifier parsing, and configured relationship-linkage conversion. Resource-type matching,
 * strict and independent identity-role selection, wire-member presence, attribute/relationship
 * ordering, synthetic-key assembly, and their shared diagnostics live in {@link
 * BasicResourceReader}.
 *
 * <p>Relationship conversion receives exactly one present {@link RelationshipData} value per
 * selected mapped relationship; the backend continues to interpret linkage shape and cardinality,
 * direct {@code ResourceIdentifier} and {@code RelationshipLinkage} handling, identifier meta, and
 * custom linkage mappers. Native failures surface as the backend's own {@code
 * JsonApiMappingException} diagnostic.
 *
 * <p>The property token {@code P} is deliberately opaque to the shared mapping domain: a backend
 * may use a Jackson deserialization-introspection record or another native representation without
 * leaking it into read semantics. This is unsupported implementation detail for backend
 * cooperation, not consumer SPI, and must not appear in supported backend signatures.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public interface ReadResourceBackend<P> {

  /**
   * Raw class of a mapped property's effective target type, for identifier-conversion diagnostics.
   */
  Class<?> rawType(ReadProperty<P> property);

  /**
   * Parses one present wire identity string through the configured identifier authority. A null
   * result means the configured conversion produced no value and is reported by the shared reader
   * as an identifier-conversion failure at the member's wire location.
   */
  @Nullable Object parseIdentifier(String wireIdentifier);

  /**
   * Converts one present relationship's linkage to the synthetic property value, exactly once per
   * selected mapped relationship. Linkage shape, cardinality, direct-value handling, identifier
   * meta, and custom linkage mappers remain backend-owned.
   */
  @Nullable Object convertRelationship(ReadProperty<P> property, RelationshipData data);
}
