package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.JsonPointers;
import com.kazforge.jsonapi.core.internal.OpenJsonValues;
import com.kazforge.jsonapi.core.internal.OrderedMaps;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Flat relationships wrapper separating semantic members from pass-through members. */
public final class Relationships {

  private static final String PATH = "/relationships";
  private static final Set<String> RESERVED = Set.of(JsonApiMembers.TYPE, JsonApiMembers.ID);

  private final Map<String, Relationship> members;
  private final Map<String, @Nullable Object> additionalMembers;

  private Relationships(
      Map<String, Relationship> members, Map<String, @Nullable Object> additionalMembers) {
    this.members = members;
    this.additionalMembers = additionalMembers;
  }

  public static Relationships empty() {
    return new Relationships(Map.of(), Map.of());
  }

  public static Relationships of(
      @Nullable Map<String, @Nullable Relationship> relationships,
      @Nullable Map<String, ?> additionalMembers) {
    Map<String, Relationship> relCopy = new LinkedHashMap<>();
    if (relationships != null && !relationships.isEmpty()) {
      for (Map.Entry<String, @Nullable Relationship> entry : relationships.entrySet()) {
        String name = entry.getKey();
        validateRelationshipName(name, JsonPointers.child(PATH, name), false);
        Relationship value = entry.getValue();
        if (value == null) {
          LocalValidation.fail(
              ValidationRuleCode.NULL_RELATIONSHIP_VALUE,
              JsonPointers.child(PATH, name),
              "Relationship value must not be null: " + name);
        }
        relCopy.put(name, value);
      }
    }
    Map<String, @Nullable Object> additionalCopy = copyAdditionalMembers(additionalMembers);
    OrderedMaps.requireNoCollisions(relCopy, additionalCopy, "relationships", PATH);
    return new Relationships(Collections.unmodifiableMap(relCopy), additionalCopy);
  }

  public static Relationships ofRelationships(
      @Nullable Map<String, @Nullable Relationship> relationships) {
    return of(relationships, Map.of());
  }

  public Map<String, Relationship> relationships() {
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

  private static void validateRelationshipName(String name, String path, boolean allowPassThrough) {
    if (MemberNames.isAtMember(name)) {
      validateAtRelationship(name, path, allowPassThrough);
      return;
    }
    if (MemberNames.isExtensionMember(name)) {
      validateExtensionRelationship(name, path, allowPassThrough);
      return;
    }
    if (!MemberNames.isValid(name)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME, path, "Invalid relationship name: " + name);
    }
    if (RESERVED.contains(name)) {
      LocalValidation.fail(
          ValidationRuleCode.RESERVED_FIELD_NAME, path, "Reserved relationship name: " + name);
    }
  }

  private static void validateAtRelationship(String name, String path, boolean allowPassThrough) {
    if (!allowPassThrough || !MemberNames.isValid(name)) {
      LocalValidation.fail(
          allowPassThrough
              ? ValidationRuleCode.INVALID_MEMBER_NAME
              : ValidationRuleCode.RESERVED_FIELD_NAME,
          path,
          allowPassThrough
              ? "Invalid relationship member name: " + name
              : "Relationship names cannot start with @: " + name);
    }
  }

  private static void validateExtensionRelationship(
      String name, String path, boolean allowPassThrough) {
    if (!allowPassThrough) {
      LocalValidation.fail(
          ValidationRuleCode.RESERVED_FIELD_NAME,
          path,
          "Extension members must use additionalMembers: " + name);
    }
    if (!MemberNames.isValid(name)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME,
          path,
          "Invalid relationship member name: " + name);
    }
  }

  private static Map<String, @Nullable Object> copyAdditionalMembers(
      @Nullable Map<String, ?> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, @Nullable Object> copy = new LinkedHashMap<String, @Nullable Object>();
    for (Map.Entry<String, ?> entry : source.entrySet()) {
      String name = entry.getKey();
      validateRelationshipName(name, JsonPointers.child(PATH, name), true);
      copy.put(name, OpenJsonValues.copy(entry.getValue(), JsonPointers.child(PATH, name)));
    }
    return OrderedMaps.copyOfNullableValues(copy);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Relationships that)) {
      return false;
    }
    return members.equals(that.members) && additionalMembers.equals(that.additionalMembers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(members, additionalMembers);
  }
}
