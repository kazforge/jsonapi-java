package com.kazforge.jsonapi.core.internal;

import com.kazforge.jsonapi.core.validation.JsonApiValidationException;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Validation and deep-copy of JSON-compatible open values. */
public final class OpenJsonValues {

  private static final Set<Class<?>> IMMUTABLE_NUMBERS =
      Set.of(
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          BigInteger.class,
          BigDecimal.class);

  private OpenJsonValues() {}

  public static boolean isValid(@Nullable Object value) {
    return isValid(value, new IdentityHashMap<>());
  }

  private static boolean isValid(
      @Nullable Object value, IdentityHashMap<Object, Boolean> visiting) {
    return switch (value) {
      case null -> true;
      case String ignored -> true;
      case Boolean ignored -> true;
      case Number number -> isSupportedNumber(number);
      case List<?> list -> isValidList(list, visiting);
      case Map<?, ?> map -> isValidMap(map, visiting);
      default -> false;
    };
  }

  public static @Nullable Object copy(@Nullable Object value) {
    return copy(value, "");
  }

  public static @Nullable Object copy(@Nullable Object value, String path) {
    return copy(value, path, new IdentityHashMap<>());
  }

  private static @Nullable Object copy(
      @Nullable Object value, String path, IdentityHashMap<Object, Boolean> visiting) {
    return switch (value) {
      case null -> null;
      case String s -> s;
      case Boolean b -> b;
      case Number number -> copyNumber(number, path);
      case List<?> list -> copyList(list, path, visiting);
      case Map<?, ?> map -> copyMapValue(map, path, visiting);
      default ->
          throw new JsonApiValidationException(
              ValidationRuleCode.INVALID_OPEN_JSON_VALUE,
              path,
              "Unsupported JSON value type: " + value.getClass().getName());
    };
  }

  public static Map<String, @Nullable Object> copyMap(@Nullable Map<String, ?> source) {
    return copyMap(source, "");
  }

  public static Map<String, @Nullable Object> copyMap(
      @Nullable Map<String, ?> source, String path) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    return copyMapValue(source, path, new IdentityHashMap<>());
  }

  public static List<String> copyStringList(@Nullable List<String> source) {
    if (source == null || source.isEmpty()) {
      return List.of();
    }
    return List.copyOf(source);
  }

  private static boolean isValidList(List<?> list, IdentityHashMap<Object, Boolean> visiting) {
    if (visiting.containsKey(list)) {
      return false;
    }
    visiting.put(list, Boolean.TRUE);
    try {
      for (Object element : list) {
        if (!isValid(element, visiting)) {
          return false;
        }
      }
      return true;
    } finally {
      visiting.remove(list);
    }
  }

  private static boolean isValidMap(Map<?, ?> map, IdentityHashMap<Object, Boolean> visiting) {
    if (visiting.containsKey(map)) {
      return false;
    }
    visiting.put(map, Boolean.TRUE);
    try {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String) || !isValid(entry.getValue(), visiting)) {
          return false;
        }
      }
      return true;
    } finally {
      visiting.remove(map);
    }
  }

  private static Number copyNumber(Number number, String path) {
    if (!isSupportedNumber(number)) {
      throw new JsonApiValidationException(
          ValidationRuleCode.INVALID_OPEN_JSON_VALUE,
          path,
          "Unsupported or non-finite number: " + number.getClass().getName());
    }
    return number;
  }

  private static List<@Nullable Object> copyList(
      List<?> list, String path, IdentityHashMap<Object, Boolean> visiting) {
    if (visiting.containsKey(list)) {
      throw new JsonApiValidationException(
          ValidationRuleCode.INVALID_OPEN_JSON_VALUE, path, "Cyclic JSON value");
    }
    visiting.put(list, Boolean.TRUE);
    try {
      List<@Nullable Object> copy = new ArrayList<@Nullable Object>(list.size());
      for (int i = 0; i < list.size(); i++) {
        copy.add(copy(list.get(i), path + "/" + i, visiting));
      }
      return OrderedMaps.copyOfNullableElements(copy);
    } finally {
      visiting.remove(list);
    }
  }

  private static Map<String, @Nullable Object> copyMapValue(
      Map<?, ?> map, String path, IdentityHashMap<Object, Boolean> visiting) {
    if (visiting.containsKey(map)) {
      throw new JsonApiValidationException(
          ValidationRuleCode.INVALID_OPEN_JSON_VALUE, path, "Cyclic JSON value");
    }
    visiting.put(map, Boolean.TRUE);
    try {
      Map<String, @Nullable Object> copy = new LinkedHashMap<String, @Nullable Object>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new JsonApiValidationException(
              ValidationRuleCode.INVALID_OPEN_JSON_VALUE, path, "Map keys must be strings");
        }
        copy.put(key, copy(entry.getValue(), appendPathToken(path, key), visiting));
      }
      return OrderedMaps.copyOfNullableValues(copy);
    } finally {
      visiting.remove(map);
    }
  }

  /** Appends a JSON Pointer token with RFC 6901 escaping (`~` → `~0`, `/` → `~1`). */
  private static String appendPathToken(String path, String token) {
    return path + "/" + token.replace("~", "~0").replace("/", "~1");
  }

  private static boolean isSupportedNumber(Number number) {
    if (!IMMUTABLE_NUMBERS.contains(number.getClass())) {
      return false;
    }
    return switch (number) {
      case Double d -> !d.isInfinite() && !d.isNaN();
      case Float f -> !f.isInfinite() && !f.isNaN();
      default -> true;
    };
  }
}
