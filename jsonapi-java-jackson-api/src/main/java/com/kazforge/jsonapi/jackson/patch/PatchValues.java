package com.kazforge.jsonapi.jackson.patch;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Shared shallow-freeze and defensive-copy convention for PATCH payload container values.
 *
 * <p>{@link List}, {@link Set}, and {@link Map} values are copied into unmodifiable wrappers once
 * at storage time; arrays are copied on every exposure so callers cannot mutate stored state
 * through an array reference. Null elements and map values are preserved. The convention matches
 * {@link PatchChange} and {@link StructuredPatch} atomic members so every PATCH payload is
 * immutable from the caller's perspective.
 */
final class PatchValues {

  private PatchValues() {}

  /** Shallow-freezes the value for storage; scalars and null pass through. */
  static @Nullable Object freeze(@Nullable Object value) {
    switch (value) {
      case null -> {
        return null;
      }
      case List<?> list -> {
        List<@Nullable Object> copy = new ArrayList<>(list.size());
        copy.addAll(list);
        return Collections.unmodifiableList(copy);
      }
      case Set<?> set -> {
        Set<@Nullable Object> copy = new LinkedHashSet<>(set);
        return Collections.unmodifiableSet(copy);
      }
      case Map<?, ?> map -> {
        Map<Object, @Nullable Object> copy = LinkedHashMap.newLinkedHashMap(map.size());
        copy.putAll(map);
        return Collections.unmodifiableMap(copy);
      }
      default -> {
        // Scalars and arrays are copied below.
      }
    }
    return copyArray(value);
  }

  /** Defensive copy for array accessors; lists/sets/maps are already unmodifiable. */
  static @Nullable Object expose(@Nullable Object value) {
    return copyArray(value);
  }

  private static @Nullable Object copyArray(@Nullable Object value) {
    if (value == null || !value.getClass().isArray()) {
      return value;
    }
    int length = Array.getLength(value);
    Object copy = Array.newInstance(value.getClass().getComponentType(), length);
    //noinspection SuspiciousSystemArraycopy
    System.arraycopy(value, 0, copy, 0, length);
    return copy;
  }
}
