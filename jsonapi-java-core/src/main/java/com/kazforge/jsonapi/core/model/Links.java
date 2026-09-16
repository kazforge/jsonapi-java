package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.internal.JsonPointers;
import com.kazforge.jsonapi.core.internal.OrderedMaps;
import com.kazforge.jsonapi.core.internal.SyntaxValidators;
import com.kazforge.jsonapi.core.validation.LinksContext;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, insertion-ordered links object with link relations separate from pass-through members.
 *
 * <p>A present relation mapped to Java {@code null} preserves an explicit JSON {@code null} link.
 * {@link #empty()} represents a present-empty links object when attached to a containing value; a
 * Java {@code null} component means the entire {@code links} member is absent. Construction checks
 * relation syntax and reserved-name separation. Aggregate validation decides which standard,
 * profile, extension, and pagination relations are valid at each document location.
 */
public final class Links {

  private static final String PATH = "/links";
  private static final Set<String> TOP_LEVEL_STANDARD =
      Set.of(
          JsonApiMembers.SELF,
          JsonApiMembers.RELATED,
          JsonApiMembers.DESCRIBEDBY,
          JsonApiMembers.FIRST,
          JsonApiMembers.LAST,
          JsonApiMembers.PREV,
          JsonApiMembers.NEXT);
  private static final Set<String> RESOURCE_STANDARD = Set.of(JsonApiMembers.SELF);
  private static final Set<String> RELATIONSHIP_STANDARD =
      Set.of(
          JsonApiMembers.SELF,
          JsonApiMembers.RELATED,
          JsonApiMembers.FIRST,
          JsonApiMembers.LAST,
          JsonApiMembers.PREV,
          JsonApiMembers.NEXT);
  private static final Set<String> ERROR_STANDARD =
      Set.of(JsonApiMembers.ABOUT, JsonApiMembers.TYPE);
  private static final Set<String> RESERVED_ADDITIONAL =
      Set.copyOf(
          Stream.of(TOP_LEVEL_STANDARD, RESOURCE_STANDARD, RELATIONSHIP_STANDARD, ERROR_STANDARD)
              .flatMap(Set::stream)
              .toList());

  private final Map<String, @Nullable Link> entries;
  private final Map<String, @Nullable Object> additionalMembers;

  private Links(
      Map<String, @Nullable Link> entries, Map<String, @Nullable Object> additionalMembers) {
    this.entries = entries;
    this.additionalMembers = additionalMembers;
  }

  /** Returns a links object with neither link relations nor pass-through members. */
  public static Links empty() {
    return new Links(Map.of(), Map.of());
  }

  /**
   * Snapshots link relations and pass-through members, preserving explicit-null link values and
   * each map's encounter order. A {@code null} input map is treated as empty.
   */
  public static Links of(
      @Nullable Map<String, @Nullable Link> links, @Nullable Map<String, ?> additionalMembers) {
    Map<String, @Nullable Link> linkCopy = copyLinkEntries(links);
    Map<String, @Nullable Object> additionalCopy = copyAdditionalMembers(additionalMembers);
    OrderedMaps.requireNoCollisions(linkCopy, additionalCopy, "links", PATH);
    return new Links(linkCopy, additionalCopy);
  }

  /** Snapshots link relations, including explicit-null values, with no pass-through members. */
  public static Links ofLinks(@Nullable Map<String, @Nullable Link> links) {
    return of(links, Map.of());
  }

  /** Returns the immutable relation map, preserving explicit-null values and encounter order. */
  public Map<String, @Nullable Link> links() {
    return entries;
  }

  /** Returns the immutable pass-through member map in encounter order. */
  public Map<String, @Nullable Object> additionalMembers() {
    return additionalMembers;
  }

  /** Whether both link relations and pass-through members are empty. */
  public boolean isEmpty() {
    return entries.isEmpty() && additionalMembers.isEmpty();
  }

  /**
   * Returns one immutable wire-member map with link relations first and pass-through members
   * second, retaining explicit-null values.
   */
  public Map<String, @Nullable Object> flatten() {
    Map<String, @Nullable Object> flat = new LinkedHashMap<String, @Nullable Object>();
    flat.putAll(entries);
    flat.putAll(additionalMembers);
    return OrderedMaps.copyOfNullableValues(flat);
  }

  /**
   * Whether {@code name} is standard for {@code context}; this does not test whether this instance
   * contains the name.
   */
  public boolean hasStandardMember(String name, LinksContext context) {
    return standardMembers(context).contains(name);
  }

  /**
   * Returns the immutable base-spec link names for a location, before profile or extension policy.
   */
  public static Set<String> standardMembers(LinksContext context) {
    return switch (context) {
      case TOP_LEVEL -> TOP_LEVEL_STANDARD;
      case RESOURCE -> RESOURCE_STANDARD;
      case RELATIONSHIP -> RELATIONSHIP_STANDARD;
      case ERROR -> ERROR_STANDARD;
    };
  }

  private static Map<String, @Nullable Link> copyLinkEntries(
      @Nullable Map<String, @Nullable Link> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, @Nullable Link> copy = new LinkedHashMap<String, @Nullable Link>();
    for (Map.Entry<String, @Nullable Link> entry : source.entrySet()) {
      String name = entry.getKey();
      if (MemberNames.isAtMember(name)) {
        LocalValidation.fail(
            ValidationRuleCode.RESERVED_FIELD_NAME,
            JsonPointers.child(PATH, name),
            "Link relation names cannot start with @: " + name);
      }
      if (MemberNames.isExtensionMember(name)) {
        if (!MemberNames.isValid(name)) {
          LocalValidation.fail(
              ValidationRuleCode.INVALID_MEMBER_NAME,
              JsonPointers.child(PATH, name),
              "Invalid link relation name: " + name);
        }
      } else if (!SyntaxValidators.isValidLinkRelation(name)) {
        LocalValidation.fail(
            ValidationRuleCode.INVALID_LINK_RELATION,
            JsonPointers.child(PATH, name),
            "Invalid link relation name: " + name);
      }
      copy.put(name, entry.getValue());
    }
    return OrderedMaps.copyOfNullableValues(copy);
  }

  private static Map<String, @Nullable Object> copyAdditionalMembers(
      @Nullable Map<String, ?> source) {
    return AdditionalMembers.copy(source, PATH, "Invalid links member name: ", RESERVED_ADDITIONAL);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Links that)) {
      return false;
    }
    return entries.equals(that.entries) && additionalMembers.equals(that.additionalMembers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entries, additionalMembers);
  }
}
