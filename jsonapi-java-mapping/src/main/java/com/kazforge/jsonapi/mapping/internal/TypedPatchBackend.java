package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared typed PATCH DTO orchestrator.
 *
 * <p>Only native mechanics live here: declared relationship-linkage identifier-meta target
 * validation against the serialization-oriented typed mapping, wire-identifier parsing, configured
 * relationship-linkage conversion for the typed path, and the single final native bean construction
 * (including construction-path extraction and identifier-construction failure reclassification).
 * Resource-type matching, typed declaration preflight, synthetic property-map and {@code
 * PresenceMarker} assembly, unknown-supplied-member strictness, meta/data gating, and the contract
 * phase order live in {@link TypedPatchBinder}.
 *
 * <p>The type token {@code T} and property token {@code P} are deliberately opaque to the shared
 * mapping domain. This is unsupported implementation detail for backend cooperation, not consumer
 * SPI, and must not appear in supported backend public signatures.
 *
 * @param <P> opaque backend-native property token
 * @param <T> opaque backend-native type token
 */
@NullMarked
// The interface is @NullMarked and its nullable parameters are explicitly annotated; the IDE
// nullness "overriding parameters" inspection misfires on this capability interface.
@SuppressWarnings("NullableProblems")
public interface TypedPatchBackend<P, T> {

  /**
   * Validates declared relationship-linkage identifier-meta targets of {@code definition} against
   * the effective typed mapping, throwing the backend's own {@code INVALID_IDENTIFIER_META_TARGET}
   * diagnostic at the property's resource-relative wire location. Invoked once during declaration
   * preflight.
   */
  void validateRelationshipLinkageMeta(TypedPatchDefinition<P, T> definition, Class<?> rawType);

  /**
   * Parses one present wire identity string into the identifier property's construction value.
   * Failures surface as the backend's own {@code IDENTIFIER_CONVERSION_FAILED} diagnostic at {@code
   * /id}.
   */
  Object parseIdentity(String wireIdentifier, Class<?> rawType);

  /**
   * Converts one supplied typed relationship linkage without coercing the complete inner target
   * type. The adapter supplies the native shape, configured mapper invocation, and identifier-meta
   * conversion while {@link RelationshipLinkageBinder} owns cardinality, null/empty
   * short-circuiting, direct identifier copying, and wrapper occurrence orchestration. Failures
   * surface as the backend's own diagnostic at the relationship's data location.
   */
  @Nullable Object convertRelationship(TypedPatchProperty<P, T> property, RelationshipData data);

  /**
   * Performs the single final native bean construction from the synthetic property map, including
   * construction-path extraction and identifier-construction failure reclassification.
   *
   * @param identifier the mapped identity role, used to reclassify a construction failure caused by
   *     the wire identifier; may be {@code null} only when the definition has no identity role
   */
  Object construct(
      T targetType,
      Map<String, @Nullable Object> properties,
      Class<?> rawType,
      @Nullable TypedPatchProperty<P, T> identifier);
}
