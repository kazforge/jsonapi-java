package com.kazforge.jsonapi.core.internal;

import com.kazforge.jsonapi.core.validation.JsonApiValidationException;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Null-preserving ordered map and list copies. */
public final class OrderedMaps {

  private OrderedMaps() {}

  public static <K, V> Map<K, @Nullable V> copyOfNullableValues(
      @Nullable Map<K, @Nullable V> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    return Collections.<K, @Nullable V>unmodifiableMap(new LinkedHashMap<>(source));
  }

  public static <E> List<@Nullable E> copyOfNullableElements(@Nullable List<@Nullable E> source) {
    if (source == null || source.isEmpty()) {
      return List.of();
    }
    return Collections.<@Nullable E>unmodifiableList(new ArrayList<>(source));
  }

  public static <K> void requireNoCollisions(
      Map<K, ?> primary, Map<K, ?> secondary, String containerName, String path) {
    for (K key : secondary.keySet()) {
      if (primary.containsKey(key)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.MEMBER_NAME_COLLISION,
            JsonPointers.child(path, String.valueOf(key)),
            "Member name collision in " + containerName + ": " + key);
      }
    }
  }

  public static void rejectReservedNames(
      @Nullable Map<String, ?> members,
      Set<String> reserved,
      String pathPrefix,
      String messagePrefix) {
    if (members == null || members.isEmpty()) {
      return;
    }
    for (String name : members.keySet()) {
      if (reserved.contains(name)) {
        LocalValidation.fail(
            ValidationRuleCode.RESERVED_FIELD_NAME,
            JsonPointers.child(pathPrefix, name),
            messagePrefix + name);
      }
    }
  }
}
