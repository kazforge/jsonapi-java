package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import org.jspecify.annotations.NullMarked;

/**
 * Backend capability boundary required by the shared basic resource writer.
 *
 * <p>The type token {@code T} and property token {@code P} are deliberately opaque to the shared
 * mapping domain: a backend may use Jackson types or another native representation without leaking
 * them into write semantics. Implementations are backend-local bridges over native mapping lookup,
 * property access, identifier conversion, property-scoped conversion, relationship normalization,
 * and relationship enrichment. Native failures surface as the backend's own {@code
 * JsonApiMappingException} diagnostic. This is unsupported implementation detail for backend
 * cooperation, not consumer SPI, and must not appear in supported backend signatures.
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

  /**
   * Reads and converts one mapped identity role, unwrapping backend transport wrappers. Returns
   * {@link IdentityRead#absent()} when the member or its value is absent, or a present read whose
   * value may be null when conversion yields no wire string.
   */
  IdentityRead identity(Object domain, WriteProperty<P> property);

  /**
   * Reads and converts one mapped attribute value for {@code domain}'s {@code declaredType},
   * preserving backend property omission and explicit-null emission state.
   */
  AttributeConversion attribute(Object domain, T declaredType, WriteProperty<P> property);

  /**
   * Normalizes one mapped relationship value into the closed ordinary/prebuilt result, translating
   * unsupported collection shapes to the backend's relationship diagnostics.
   */
  RelationshipValue<T> normalizeRelationship(
      Object domain, T declaredType, WriteProperty<P> property);

  /** Resolves the effective runtime type while retaining backend-native generic information. */
  T effectiveType(Object domain, T declaredType);

  /**
   * Applies backend-owned relationship enrichment, such as relationship meta, to linkage the shared
   * writer has just built. Called once per selected relationship immediately after that
   * relationship's linkage is constructed, so enrichment ordering matches backend reads.
   */
  Relationship enrichRelationship(
      Object domain, T declaredType, WriteProperty<P> property, RelationshipData linkage);
}
