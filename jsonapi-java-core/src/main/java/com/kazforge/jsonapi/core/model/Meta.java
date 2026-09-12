package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.JsonPointers;
import com.kazforge.jsonapi.core.internal.OpenJsonValues;
import com.kazforge.jsonapi.core.internal.OrderedMaps;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Flat, insertion-ordered JSON-compatible metadata members. */
public final class Meta {

  private final Map<String, @Nullable Object> members;

  private Meta(Map<String, @Nullable Object> members) {
    this.members = members;
  }

  public static Meta empty() {
    return new Meta(Map.of());
  }

  public static Meta of(@Nullable Map<String, ?> members) {
    if (members == null || members.isEmpty()) {
      return empty();
    }
    Map<String, @Nullable Object> copy = new LinkedHashMap<String, @Nullable Object>();
    for (Map.Entry<String, ?> entry : members.entrySet()) {
      String name = entry.getKey();
      validateMemberName(name, JsonPointers.child("/meta", name));
      copy.put(name, OpenJsonValues.copy(entry.getValue(), JsonPointers.child("/meta", name)));
    }
    return new Meta(OrderedMaps.copyOfNullableValues(copy));
  }

  public Map<String, @Nullable Object> members() {
    return members;
  }

  public boolean isEmpty() {
    return members.isEmpty();
  }

  private static void validateMemberName(String name, String path) {
    if (!MemberNames.isValid(name)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME, path, "Invalid meta member name: " + name);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Meta meta)) {
      return false;
    }
    return members.equals(meta.members);
  }

  @Override
  public int hashCode() {
    return Objects.hash(members);
  }

  @Override
  public String toString() {
    return "Meta" + members;
  }
}
