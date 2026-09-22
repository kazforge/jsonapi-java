package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared resource reader.
 *
 * <p>Only native mechanics live here: the raw diagnostic class of a mapped property, configured
 * wire-identifier parsing, lazy relationship-shape resolution (target/type resolution plus
 * configured-mapper selection), configured linkage-mapper invocation, and declared identifier-meta
 * conversion. Resource-type matching, strict and independent identity-role selection, wire-member
 * presence, attribute/relationship ordering, synthetic-key assembly, cardinality validation,
 * null/empty short-circuiting, direct identifier copying, wrapper occurrence orchestration,
 * identifier-meta sequencing, resource/relationship meta presence and binding, and their shared
 * diagnostics live in {@link BasicResourceReader}.
 *
 * <p>Relationship shape resolution happens lazily, only after the shared reader finds supplied
 * relationship {@code data} and validates the property's bindability, so an unsupported or
 * unresolvable target still wins for supplied null/empty linkage while no mapper is invoked for
 * those empty states. {@link #mapLinkage} receives exactly one present {@link RelationshipData}
 * value for a non-empty mapped branch: the whole collection linkage for an ordinary to-many
 * relationship, one single linkage per wrapped to-many occurrence, or the to-one linkage. Native
 * failures surface as the backend's own {@code JsonApiMappingException} diagnostic.
 *
 * <p>The type token {@code T} and property token {@code P} are deliberately opaque to the shared
 * mapping domain: a backend may use Jackson types or another native representation without leaking
 * them into read semantics. This is unsupported implementation detail for backend cooperation, not
 * consumer SPI, and must not appear in supported backend signatures.
 *
 * @param <T> opaque backend-native type token
 * @param <P> opaque backend-native property token
 */
@NullMarked
public interface ReadResourceBackend<T, P> {

  /**
   * Raw class of a mapped property's effective target type, for identifier-conversion and
   * cardinality diagnostics.
   */
  Class<?> rawType(ReadProperty<P> property);

  /**
   * Parses one present wire identity string through the configured identifier authority. A null
   * result means the configured conversion produced no value and is reported by the shared reader
   * as an identifier-conversion failure at the member's wire location.
   */
  @Nullable Object parseIdentifier(String wireIdentifier);

  /**
   * Resolves the declared neutral read shape of one mapped relationship property. Called lazily by
   * the shared reader only after supplied relationship {@code data} is present and the property is
   * bindable. Target/type resolution and configured-mapper selection happen here, so an unsupported
   * or unresolvable target fails before the shared cardinality and null/empty short-circuit checks.
   */
  ReadRelationshipShape<T> readRelationshipShape(ReadProperty<P> property);

  /**
   * Invokes the configured linkage mapper for one non-empty, cardinality-valid mapped linkage
   * branch and returns the synthetic property value, exactly once per selected mapped relationship
   * (or once per wrapped to-many occurrence). A null result is returned unchanged: an ordinary
   * to-one mapper result binds a null property, a wrapped to-one null target yields no wrapper, and
   * a wrapped to-many null target is failed by the shared reader at that occurrence's indexed
   * relationship-data location.
   */
  @Nullable Object mapLinkage(ReadProperty<P> property, RelationshipData data, T target);

  /**
   * Converts one present wrapper occurrence's identifier {@link Meta} to the declared
   * identifier-meta token. The shared reader decides presence, occurrence index, and the
   * identifier-meta location; conversion failures surface as the backend's own identifier-meta
   * diagnostic at that location.
   */
  @Nullable Object convertIdentifierMeta(
      ReadProperty<P> property, Meta meta, T metaToken, int occurrenceIndex);
}
