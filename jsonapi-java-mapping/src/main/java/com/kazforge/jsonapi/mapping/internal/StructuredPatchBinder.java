package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.internal.patch.PresenceMarker;
import com.kazforge.jsonapi.patch.StructuredMember;
import com.kazforge.jsonapi.patch.StructuredMemberState;
import com.kazforge.jsonapi.patch.StructuredPatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral recursive structured-value PATCH engine shared by the low-level {@code
 * PatchCommand} path and the direct typed PATCH DTO path.
 *
 * <p>It owns member resolution policy over adapter-supplied native facts, shape and boundary
 * classification, low-level nested conversion orchestration, typed marker-tree construction, null
 * policy, wire-pointer accumulation, and lazy nested declaration validation. It has no resource
 * mapping, Jackson, or annotation dependency: the adapter supplies a {@link StructuredShapeBackend}
 * with the resolved shape, the native type queries, and atomic conversion, so structured JSON:API
 * {@code meta} mapping can reuse the same machinery at its own location with a stricter outer-state
 * policy.
 *
 * <p>The typed entry point recurses only through deliberately presence-aware nested PATCH shapes
 * (every visible member exactly {@code PatchPresence<T>}, no wrapper-level customization); the
 * low-level entry points derive supplied-only nested changes from ordinary structured domain value
 * types under the traversable-bean + object-wire boundary, with {@link java.util.Optional} as a
 * transparent qualification wrapper and a single {@code PatchPresence} wrapper unwrap.
 * Presence-aware PATCH shapes are a typed-path concept: on the low-level path a {@code
 * PatchPresence<T>} member whose inner type is a presence-aware shape fails loudly with {@link
 * MappingDiagnostic#INVALID_PATCH_PROPERTY_TYPE} at the accumulated pointer.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
public final class StructuredPatchBinder<T> {

  /** Low-level traversal decision for one member. */
  public enum LowLevelKind {
    /** The declared type traverses into a {@link StructuredPatch} for this object wire value. */
    RECURSE,
    /** The member stays an atomic converted value. */
    ATOMIC
  }

  private final StructuredShapeBackend<T> backend;

  public StructuredPatchBinder(StructuredShapeBackend<T> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  // ============================== TYPED MODE ==============================

  /**
   * Binds one supplied member value for the typed PATCH DTO path.
   *
   * <p>{@code declaredPatchPresenceType} must be exactly {@code PatchPresence<T>} (top-level
   * members are validated eagerly by the typed binder; nested members by the engine on shape
   * entry). Returns the value to place inside an internal {@link PresenceMarker} with {@code
   * present=true}: the original JSON-compatible atomic value, {@code null}, or a nested marker map
   * that the single whole-tree {@code convertValue} reads through the inner type (preserving the
   * strict marker invariant).
   */
  public @Nullable Object typedMemberValue(
      @Nullable Object wire,
      T declaredPatchPresenceType,
      MappingLocation pointer,
      Class<?> rawType) {
    T inner = backend.patchPresenceInner(declaredPatchPresenceType);
    T effective = unwrapOptional(inner);
    if (wire == null) {
      if (backend.isPrimitive(effective)) {
        throw unsupported(
            rawType,
            pointer,
            "Explicit null is not supported for a primitive nested PATCH member at '"
                + pointer
                + "'");
      }
      return null;
    }
    StructuredShape<T> shape = shapeOf(effective);
    if (shape == null) {
      return wire;
    }
    if (shape.presenceAware()) {
      if (wire instanceof Map<?, ?> map) {
        return bindTypedShape(map, shape, pointer, rawType);
      }
      return wire;
    }
    if (shape.mixed()) {
      throw invalidPatchPropertyType(
          rawType,
          pointer,
          "mixed nested PATCH shape: every visible member of "
              + backend.typeName(effective)
              + " must be declared exactly as PatchPresence<T> when the shape is used as a nested "
              + "PATCH shape");
    }
    return wire;
  }

  private Map<String, Object> bindTypedShape(
      Map<?, ?> wire, StructuredShape<T> shape, MappingLocation pointer, Class<?> rawType) {
    validateTypedShape(shape, pointer, rawType);
    for (Object key : wire.keySet()) {
      String name = key instanceof String string ? string : String.valueOf(key);
      if (shape.memberByWire(name) == null) {
        throw unknownPatchMember(rawType, pointer.append(name), name);
      }
    }
    Map<String, Object> markerMap = LinkedHashMap.newLinkedHashMap(shape.members().size());
    for (StructuredShape.Member<T> member : shape.members()) {
      String wireName = member.wireName();
      if (wire.containsKey(wireName)) {
        markerMap.put(
            wireName,
            new PresenceMarker(
                true,
                typedMemberValue(
                    wire.get(wireName), member.declaredType(), pointer.append(wireName), rawType)));
      } else {
        markerMap.put(wireName, new PresenceMarker(false, null));
      }
    }
    return markerMap;
  }

  /** On shape entry, every visible member must satisfy the strict presence-aware contract. */
  private void validateTypedShape(
      StructuredShape<T> shape, MappingLocation pointer, Class<?> rawType) {
    for (StructuredShape.Member<T> member : shape.members()) {
      if (member.wrapperCustomization()) {
        throw invalidPatchPropertyType(
            rawType,
            pointer.append(member.wireName()),
            "nested PATCH member '"
                + member.wireName()
                + "' must not carry wrapper-level @JsonDeserialize/@JsonSerialize customization");
      }
    }
  }

  // ============================== LOW-LEVEL MODE ==============================

  /**
   * Decides whether one supplied member value recurses into a {@link StructuredPatch} on the
   * low-level path. Throws {@link MappingDiagnostic#INVALID_PATCH_PROPERTY_TYPE} when the declared
   * type unwraps to a presence-aware PATCH shape (a typed-path concept).
   *
   * @param deserializationCustomization whether the member carries property-scoped deserialization
   *     customization that must force atomic conversion
   */
  public LowLevelKind lowLevelKind(
      T declaredType,
      @Nullable Object wire,
      boolean deserializationCustomization,
      MappingLocation pointer,
      Class<?> rawType) {
    if (!(wire instanceof Map<?, ?>)) {
      return LowLevelKind.ATOMIC;
    }
    boolean viaPatchPresence = backend.isPatchPresence(declaredType);
    T beanType = unwrapLowLevel(declaredType);
    if (deserializationCustomization) {
      return LowLevelKind.ATOMIC;
    }
    StructuredShape<T> shape = shapeOf(beanType);
    if (shape == null) {
      return LowLevelKind.ATOMIC;
    }
    if (viaPatchPresence && shape.presenceAware()) {
      throw invalidPatchPropertyType(
          rawType,
          pointer,
          "presence-aware nested PATCH shapes are a typed-path concept: PatchPresence<"
              + backend.typeName(beanType)
              + "> is not supported on the low-level PatchCommand<T> path at '"
              + pointer
              + "'");
    }
    return LowLevelKind.RECURSE;
  }

  /**
   * Recursively binds one supplied object wire value into a {@link StructuredPatch} for the
   * low-level path. The caller must have decided {@link LowLevelKind#RECURSE} for this member.
   * Supplied-only nested members are produced in declaration order; unknown wire members are
   * skipped (lossless change-list contract).
   */
  public Object bindLowLevelStructured(
      @Nullable Object wire, T declaredType, MappingLocation pointer, Class<?> rawType) {
    T beanType = unwrapLowLevel(declaredType);
    StructuredShape<T> shape = Objects.requireNonNull(shapeOf(beanType), "shape");
    Map<?, ?> wireMap = (Map<?, ?>) Objects.requireNonNull(wire, "wire");
    List<StructuredMember> members = new ArrayList<>();
    for (StructuredShape.Member<T> member : shape.members()) {
      String wireName = member.wireName();
      if (wireMap.containsKey(wireName)) {
        members.add(
            bindLowLevelMember(
                wireMap.get(wireName), member, beanType, pointer.append(wireName), rawType));
      }
    }
    return new StructuredPatch(members);
  }

  private StructuredMember bindLowLevelMember(
      @Nullable Object wire,
      StructuredShape.Member<T> member,
      T beanType,
      MappingLocation pointer,
      Class<?> rawType) {
    LowLevelKind kind =
        lowLevelKind(
            member.declaredType(), wire, member.deserializationCustomization(), pointer, rawType);
    if (kind == LowLevelKind.RECURSE) {
      StructuredPatch nested =
          (StructuredPatch) bindLowLevelStructured(wire, member.declaredType(), pointer, rawType);
      return new StructuredMember(
          member.wireName(),
          member.internalName(),
          new StructuredMemberState.Structured(nested.members()));
    }
    return new StructuredMember(
        member.wireName(),
        member.internalName(),
        new StructuredMemberState.Atomic(atomicLowLevel(wire, member, beanType, pointer, rawType)));
  }

  private @Nullable Object atomicLowLevel(
      @Nullable Object wire,
      StructuredShape.Member<T> member,
      T beanType,
      MappingLocation pointer,
      Class<?> rawType) {
    T target = unwrapPatchPresence(member.declaredType());
    if (wire == null && backend.isPrimitive(target)) {
      throw unsupported(
          rawType,
          pointer,
          "Explicit null is not supported for a primitive nested member at '" + pointer + "'");
    }
    return backend.convertAtomic(
        beanType, member.declaredType(), target, member.wireName(), wire, pointer, rawType);
  }

  // ============================== SHARED SHAPE ACCESS ==============================

  /**
   * Returns the presence-aware typed shape reached from {@code type} after unwrapping {@code
   * Optional} and a single {@code PatchPresence}, or {@code null} when {@code type} does not lead
   * to one. Adapters use this to walk resolved shapes when translating native construction-failure
   * paths; walking itself stays adapter-owned.
   */
  public @Nullable StructuredShape<T> typedShape(T type) {
    StructuredShape<T> shape = shapeOf(unwrapOptional(unwrapPatchPresence(type)));
    if (shape == null || !shape.presenceAware()) {
      return null;
    }
    return shape;
  }

  // ============================== HELPERS ==============================

  private @Nullable StructuredShape<T> shapeOf(T type) {
    return backend.shapeOf(type);
  }

  private T unwrapPatchPresence(T type) {
    return backend.isPatchPresence(type) ? backend.patchPresenceInner(type) : type;
  }

  private T unwrapOptional(T type) {
    T current = type;
    while (backend.isOptional(current)) {
      current = backend.optionalInner(current);
    }
    return current;
  }

  private T unwrapLowLevel(T type) {
    return unwrapOptional(unwrapPatchPresence(type));
  }

  private static JsonApiMappingException unknownPatchMember(
      Class<?> rawType, MappingLocation path, String name) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNKNOWN_PATCH_MEMBER,
        rawType,
        path,
        "Unknown supplied nested member '" + name + "' at '" + path + "'");
  }

  private static JsonApiMappingException invalidPatchPropertyType(
      Class<?> rawType, MappingLocation path, String detail) {
    return new JsonApiMappingException(
        MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE,
        rawType,
        path,
        "Invalid nested PATCH property type at '" + path + "': " + detail);
  }

  private static JsonApiMappingException unsupported(
      Class<?> rawType, MappingLocation path, String message) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE, rawType, path, message);
  }
}
