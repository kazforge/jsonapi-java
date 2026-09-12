package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** A JSON:API relationship with optional data, links, meta, and additional members. */
public record Relationship(
    @Nullable RelationshipData data,
    @Nullable Links links,
    @Nullable Meta meta,
    Map<String, @Nullable Object> additionalMembers) {

  private static final String PATH = "/relationships";

  private static final Set<String> RESERVED_ADDITIONAL =
      Set.of(JsonApiMembers.DATA, JsonApiMembers.LINKS, JsonApiMembers.META);

  public Relationship {
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers, PATH, "Invalid relationship member name: ", RESERVED_ADDITIONAL);
    boolean hasExtension = hasExtensionMembers(additionalMembers);
    boolean hasMember = data != null || links != null || meta != null || hasExtension;
    if (!hasMember) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER,
          PATH,
          "Relationship must contain at least one of data, links, meta, or extension members");
    }
    if (data == null && meta == null && !hasExtension && !hasNonPaginationRelationshipLink(links)) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER,
          PATH,
          "Links-only relationship must contain a non-pagination link"
              + " (self, related, extension, or profile relation)");
    }
  }

  public static Relationship linkOnly(Links links) {
    return new Relationship(null, links, null, Map.of());
  }

  public static Relationship metaOnly(Meta meta) {
    return new Relationship(null, null, meta, Map.of());
  }

  public static Relationship withData(RelationshipData data) {
    return new Relationship(data, null, null, Map.of());
  }

  public boolean hasDataMember() {
    return data != null;
  }

  private static boolean hasExtensionMembers(@Nullable Map<String, ?> members) {
    if (members == null || members.isEmpty()) {
      return false;
    }
    for (String name : members.keySet()) {
      if (MemberNames.isExtensionMember(name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNonPaginationRelationshipLink(@Nullable Links links) {
    if (links == null) {
      return false;
    }
    for (String name : links.links().keySet()) {
      if (!JsonApiMembers.PAGINATION_LINKS.contains(name)) {
        return true;
      }
    }
    return false;
  }
}
