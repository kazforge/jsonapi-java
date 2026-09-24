package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Binds one supplied typed PATCH DTO member value into the JSON-compatible value placed inside a
 * {@link PresenceMarker}.
 *
 * <p>Implemented by each adapter over its shared recursive structured-value engine, so the shared
 * typed PATCH orchestrator can assemble nested marker trees without depending on any Jackson
 * package.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 */
@NullMarked
@FunctionalInterface
public interface TypedMemberValueBinder<T> {

  @Nullable Object typedMemberValue(
      @Nullable Object wire,
      T declaredPatchPresenceType,
      MappingLocation pointer,
      Class<?> rawType);
}
