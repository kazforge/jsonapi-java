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
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Flat attributes wrapper separating semantic members from pass-through members. */
public final class Attributes {

  private static final String PATH = "/attributes";
  private static final Set<String> RESERVED = Set.of(JsonApiMembers.TYPE, JsonApiMembers.ID);

  private final Map<String, @Nullable Object> members;
  private final Map<String, @Nullable Object> additionalMembers;

  private Attributes(
      Map<String, @Nullable Object> members, Map<String, @Nullable Object> additionalMembers) {
    this.members = members;
    this.additionalMembers = additionalMembers;
  }

  public static Attributes empty() {
    return new Attributes(Map.of(), Map.of());
  }

  public static Attributes of(
      @Nullable Map<String, ?> attributes, @Nullable Map<String, ?> additionalMembers) {
    Map<String, @Nullable Object> attrCopy = copyMembers(attributes, false);
    Map<String, @Nullable Object> additionalCopy = copyMembers(additionalMembers, true);
    OrderedMaps.requireNoCollisions(attrCopy, additionalCopy, "attributes", PATH);
    return new Attributes(attrCopy, additionalCopy);
  }

  public static Attributes ofAttributes(@Nullable Map<String, ?> attributes) {
    return of(attributes, Map.of());
  }

  public Map<String, @Nullable Object> attributes() {
    return members;
  }

  public Map<String, @Nullable Object> additionalMembers() {
    return additionalMembers;
  }

  public boolean isEmpty() {
    return members.isEmpty() && additionalMembers.isEmpty();
  }

  public Map<String, @Nullable Object> flatten() {
    Map<String, @Nullable Object> flat = new LinkedHashMap<String, @Nullable Object>();
    flat.putAll(members);
    flat.putAll(additionalMembers);
    return OrderedMaps.copyOfNullableValues(flat);
  }

  private static Map<String, @Nullable Object> copyMembers(
      @Nullable Map<String, ?> source, boolean allowPassThrough) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, @Nullable Object> copy = new LinkedHashMap<String, @Nullable Object>();
    for (Map.Entry<String, ?> entry : source.entrySet()) {
      String name = entry.getKey();
      validateAttributeName(name, allowPassThrough);
      copy.put(name, OpenJsonValues.copy(entry.getValue(), JsonPointers.child(PATH, name)));
    }
    return OrderedMaps.copyOfNullableValues(copy);
  }

  private static void validateAttributeName(String name, boolean allowPassThrough) {
    if (MemberNames.isAtMember(name)) {
      validateAtAttribute(name, allowPassThrough);
      return;
    }
    if (MemberNames.isExtensionMember(name)) {
      validateExtensionAttribute(name, allowPassThrough);
      return;
    }
    if (!MemberNames.isValid(name)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME,
          JsonPointers.child(PATH, name),
          "Invalid attribute name: " + name);
    }
    if (RESERVED.contains(name)) {
      LocalValidation.fail(
          ValidationRuleCode.RESERVED_FIELD_NAME,
          JsonPointers.child(PATH, name),
          "Reserved attribute name: " + name);
    }
  }

  private static void validateAtAttribute(String name, boolean allowPassThrough) {
    if (!allowPassThrough || !MemberNames.isValid(name)) {
      LocalValidation.fail(
          allowPassThrough
              ? ValidationRuleCode.INVALID_MEMBER_NAME
              : ValidationRuleCode.RESERVED_FIELD_NAME,
          JsonPointers.child(PATH, name),
          allowPassThrough
              ? "Invalid attribute member name: " + name
              : "Attribute names cannot start with @: " + name);
    }
  }

  private static void validateExtensionAttribute(String name, boolean allowPassThrough) {
    if (!allowPassThrough) {
      LocalValidation.fail(
          ValidationRuleCode.RESERVED_FIELD_NAME,
          JsonPointers.child(PATH, name),
          "Extension members must use additionalMembers: " + name);
    }
    if (!MemberNames.isValid(name)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME,
          JsonPointers.child(PATH, name),
          "Invalid attribute member name: " + name);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Attributes that)) {
      return false;
    }
    return members.equals(that.members) && additionalMembers.equals(that.additionalMembers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(members, additionalMembers);
  }
}
