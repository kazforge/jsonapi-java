package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared basic resource writer.
 *
 * <p>Only native mechanics live here: mapping lookup, property access, identifier conversion,
 * configured attribute conversion, and native type specialization. Advanced relationship handling
 * (direct identifiers, linkage data, wrapper forms, container semantics, and identifier meta) and
 * whole-meta conversion stay in the backend's own write orchestration, which the shared writer
 * reaches through {@link BasicRelationshipWriter} rather than reinterpreting those forms itself.
 * Native failures surface as the backend's own {@code JsonApiMappingException} diagnostic.
 *
 * <p>The type token {@code T} and property token {@code P} are deliberately opaque to the shared
 * mapping domain: a backend may use Jackson types or another native representation without leaking
 * them into write semantics. This is unsupported implementation detail for backend cooperation, not
 * consumer SPI, and must not appear in supported backend signatures.
 *
 * @param <T> opaque backend-native type token
 * @param <P> opaque backend-native property token
 */
@NullMarked
public interface WriteResourceBackend<T, P> {

  /**
   * Resolves the neutral write definition for a complete declared type. Backend resolution
   * diagnostics, including unresolved generic member types, surface here.
   */
  WriteResourceDefinition<P> definition(T declaredType);

  /** Reads one mapped property value with the backend's own access diagnostics. */
  @Nullable Object readValue(Object domain, WriteProperty<P> property);

  /**
   * Converts one present identity value to its wire string through the configured identifier
   * authority; a null result means the value has no wire identity.
   */
  @Nullable String convertIdentifier(@Nullable Object value);

  /**
   * Reads and converts one mapped attribute value for {@code domain}'s {@code declaredType},
   * preserving backend property omission and explicit-null emission state.
   */
  AttributeConversion convertAttribute(Object domain, T declaredType, WriteProperty<P> property);

  /** Resolves the effective runtime type while retaining backend-native generic information. */
  T effectiveType(Object domain, T declaredType);
}
