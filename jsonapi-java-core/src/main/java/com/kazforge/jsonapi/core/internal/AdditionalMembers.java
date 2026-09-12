package com.kazforge.jsonapi.core.internal;

import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Shared copy/validation for fixed-shape additional members. */
public final class AdditionalMembers {

  private AdditionalMembers() {}

  public static Map<String, @Nullable Object> copy(
      @Nullable Map<String, ?> source, String basePath, String invalidMessage) {
    return copy(source, basePath, invalidMessage, Set.of());
  }

  public static Map<String, @Nullable Object> copy(
      @Nullable Map<String, ?> source,
      String basePath,
      String invalidMessage,
      Set<String> reservedNames) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    OrderedMaps.rejectReservedNames(source, reservedNames, basePath, "Reserved member name: ");
    Map<String, @Nullable Object> copy = new LinkedHashMap<String, @Nullable Object>();
    for (Map.Entry<String, ?> entry : source.entrySet()) {
      String name = entry.getKey();
      if (!MemberNames.isValid(name)) {
        LocalValidation.fail(
            ValidationRuleCode.INVALID_MEMBER_NAME, pathFor(basePath, name), invalidMessage + name);
      }
      copy.put(name, OpenJsonValues.copy(entry.getValue(), pathFor(basePath, name)));
    }
    return OrderedMaps.copyOfNullableValues(copy);
  }

  private static String pathFor(String basePath, String name) {
    return JsonPointers.child(basePath, name);
  }
}
