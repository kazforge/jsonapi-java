package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared low-level PATCH command binder.
 *
 * <p>Only native mechanics live here: declared whole-meta target validation against the effective
 * inbound PATCH property types, identity parsing and property conversion, atomic attribute and
 * whole-meta conversion, final relationship target/container coercion, and the linkage mechanics
 * inherited from {@link RelationshipLinkageBinder.Backend}. Object-wire structured attributes and
 * meta are decided and recursed by {@link StructuredPatchBinder} through {@link
 * StructuredShapeBackend}, not here. Resource-type matching, required {@code id} identity
 * selection, supplied-member classification, lookup by JSON:API name, change construction, {@code
 * PatchCommand} assembly, and the complete phase order live in {@link PatchCommandBinder}.
 *
 * <p>The type token {@code T} and property token {@code N} are deliberately opaque to the shared
 * mapping domain. This is unsupported implementation detail for backend cooperation, not consumer
 * SPI, and must not appear in supported backend signatures.
 *
 * @param <T> opaque backend-native type token
 * @param <N> opaque backend-native property token
 */
@NullMarked
public interface PatchResourceBackend<T, N>
    extends RelationshipLinkageBinder.Backend<T, PatchProperty<N>> {

  /**
   * Validates every declared whole-meta target of {@code definition} (resource meta, relationship
   * meta, and identifier meta on opt-in linkage) against the effective inbound PATCH property
   * types, throwing the backend's own {@code INVALID_META_TARGET} / {@code
   * INVALID_IDENTIFIER_META_TARGET} diagnostic at the property's resource-relative wire location.
   * The shared binder invokes this once, after resource-type matching and before identity
   * conversion.
   */
  void validateDeclaredMetaTargets(PatchResourceDefinition<N> definition, Class<?> rawType);

  /**
   * Parses one present wire identity string and converts it through the identifier property's
   * effective deserialization target. Failures surface as the backend's own {@code
   * IDENTIFIER_CONVERSION_FAILED} diagnostic at {@code /id}.
   */
  Object convertIdentity(
      String wireIdentifier, PatchProperty<N> identifier, T beanType, Class<?> rawType);

  /**
   * Converts one supplied mapped attribute value. When the wire value is an object that the shared
   * {@link StructuredPatchBinder} decides to recurse, the adapter reaches {@link
   * StructuredShapeBackend} for the structured traversal and native atomic conversion instead of
   * this method. Failures surface as the backend's own diagnostic at {@code location}.
   */
  @Nullable Object convertAttribute(
      PatchProperty<N> property,
      @Nullable Object rawValue,
      T beanType,
      MappingLocation location,
      Class<?> rawType);

  /**
   * Converts one supplied whole-object resource- or relationship-meta value. When the wire value is
   * an object that the shared {@link StructuredPatchBinder} decides to recurse, the adapter reaches
   * {@link StructuredShapeBackend} for the structured traversal and native atomic conversion
   * instead of this method. Failures surface as the backend's own diagnostic at {@code location}.
   */
  @Nullable Object convertWholeMeta(
      PatchProperty<N> property,
      @Nullable Object rawValue,
      T beanType,
      MappingLocation location,
      Class<?> rawType);

  /**
   * Applies final native target/container coercion to one already-orchestrated relationship value
   * (for example a {@code List} of {@link com.kazforge.jsonapi.core.model.ResourceIdentifier} into
   * a declared {@code Set}, array, or {@link java.util.Optional}). Failures surface as the
   * backend's own diagnostic at the relationship's data location.
   */
  @Nullable Object coerceRelationship(PatchProperty<N> property, @Nullable Object value);
}
