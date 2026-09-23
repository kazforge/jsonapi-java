package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Thin backend capability boundary required by the shared recursive structured-value PATCH binder.
 *
 * <p>Only native mechanics live here: structured-shape discovery (configured introspection of
 * visible, bindable members with their resolved declared types, names, and wrapper-customization
 * facts), the native type queries the shared traversal needs to unwrap {@code PatchPresence} and
 * {@link java.util.Optional} and to detect primitives, and atomic member conversion through the
 * backend's property-scoped authority. Shape caching stays adapter-owned.
 *
 * <p>The type token {@code T} is deliberately opaque to the shared mapping domain. This is
 * unsupported implementation detail for backend cooperation, not consumer SPI, and must not appear
 * in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
public interface StructuredShapeBackend<T> {

  /**
   * Resolves the structured shape of {@code type} when it is a traversable bean, or {@code null}
   * for atomic types (scalars, containers, custom/scalar deserializers, unresolved types).
   */
  @Nullable StructuredShape<T> shapeOf(T type);

  /** True when {@code type} is exactly {@code PatchPresence<T>} with one type argument. */
  boolean isPatchPresence(T type);

  /** Returns the single type argument of an exact {@code PatchPresence<T>}. */
  T patchPresenceInner(T type);

  /** True when {@code type} is exactly {@link java.util.Optional}{@code <T>}. */
  boolean isOptional(T type);

  /** Returns the single type argument of an exact {@link java.util.Optional}{@code <T>}. */
  T optionalInner(T type);

  /** True when {@code type} is a Java primitive. */
  boolean isPrimitive(T type);

  /** Canonical display name for {@code type}, used only in diagnostics. */
  String typeName(T type);

  /**
   * Converts one atomic low-level member value natively through the backend's property-scoped
   * authority. {@code beanType} is the containing bean's unwrapped type, {@code declaredType} the
   * member's declared type, and {@code targetType} the effective conversion target after a single
   * {@code PatchPresence} unwrap. Failures surface as the backend's own {@code
   * UNSUPPORTED_ATTRIBUTE_VALUE} diagnostic at {@code pointer}.
   */
  @Nullable Object convertAtomic(
      T beanType,
      T declaredType,
      T targetType,
      String wireName,
      @Nullable Object wire,
      MappingLocation pointer,
      Class<?> rawType);
}
