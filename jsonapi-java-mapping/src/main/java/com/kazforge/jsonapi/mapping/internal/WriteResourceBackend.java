package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared resource writer.
 *
 * <p>Only native mechanics live here: mapping lookup, property access, identifier conversion,
 * configured attribute and whole-meta conversion, declared identifier-meta conversion,
 * relationship-shape and target resolution, and native type specialization. Relationship-member
 * assembly, meta attachment, identifier overlay, and their shared diagnostics live in {@link
 * BasicResourceWriter}. Native failures surface as the backend's own {@code
 * JsonApiMappingException} diagnostic.
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
  MemberConversion convertAttribute(Object domain, T declaredType, WriteProperty<P> property);

  /**
   * Converts one already-read whole-meta property value through the configured property-scoped
   * authority, preserving backend omission and explicit-null emission state. {@code rawValue} is
   * the value the backend's own property access returned (kept for configured property-writer
   * resolution) and {@code unwrappedValue} is the {@link java.util.Optional}-unwrapped value used
   * as the conversion fallback. Conversion failures propagate as runtime exceptions for the shared
   * writer to translate at the correct Meta location.
   */
  MemberConversion convertWholeMeta(
      Object domain,
      T declaredType,
      WriteProperty<P> property,
      @Nullable Object rawValue,
      @Nullable Object unwrappedValue);

  /**
   * Converts one present wrapper occurrence's identifier meta through the configured declared-type
   * authority, preserving backend omission and explicit-null emission state. {@code
   * declaredMetaToken} is the wrapper's declared identifier-meta token. Conversion failures
   * propagate as runtime exceptions for the shared writer to translate at the occurrence's
   * identifier-meta location.
   */
  MemberConversion convertIdentifierMeta(T declaredMetaToken, @Nullable Object metaValue);

  /** Derives the declared neutral shape of one mapped relationship property. */
  RelationshipShape<T> relationshipShape(WriteProperty<P> property);

  /**
   * Resolves the effective target token for one ordinary relationship domain-object branch. {@code
   * relationshipToMany} is the declared cardinality of the relationship property whose linkage is
   * being built; {@code declaredTarget} is that branch's declared target token and may be
   * unresolvable ({@code null}); {@code relationshipLocation} is the relationship's
   * resource-relative {@code data} location for backend resolution diagnostics.
   */
  T resolveRelationshipTarget(
      @Nullable Object target,
      @Nullable T declaredTarget,
      boolean relationshipToMany,
      MappingLocation relationshipLocation);

  /** Resolves the effective runtime type while retaining backend-native generic information. */
  T effectiveType(Object domain, T declaredType);
}
