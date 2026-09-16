package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Resource identifier with independent {@code id} and {@code lid} members.
 *
 * <p>At least one identity member is required, and both may be present. {@link #identityKey()} uses
 * {@code id} when both are present; aggregate validation uses the pair to bind document-scoped
 * aliases.
 */
public record ResourceIdentifier(
    String type,
    @Nullable String id,
    @Nullable String lid,
    @Nullable Meta meta,
    Map<String, @Nullable Object> additionalMembers) {

  private static final Set<String> RESERVED_ADDITIONAL =
      Set.of(JsonApiMembers.TYPE, JsonApiMembers.ID, JsonApiMembers.LID, JsonApiMembers.META);

  public ResourceIdentifier {
    requireType(type);
    if (!MemberNames.isValid(type)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME, "/data/type", "Invalid resource type: " + type);
    }
    boolean hasId = id != null;
    boolean hasLid = lid != null;
    if (!hasId && !hasLid) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_RESOURCE_ID,
          "/data",
          "Resource identifier requires id or lid");
    }
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers,
            "/data",
            "Invalid resource identifier member name: ",
            RESERVED_ADDITIONAL);
  }

  /** Creates an identifier with {@code type} and {@code id}. */
  public static ResourceIdentifier of(String type, String id) {
    return new ResourceIdentifier(type, id, null, null, Map.of());
  }

  /** Creates an identifier with {@code type} and {@code lid}. */
  public static ResourceIdentifier withLid(String type, String lid) {
    return new ResourceIdentifier(type, null, lid, null, Map.of());
  }

  private static void requireType(@Nullable String type) {
    if (type == null) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_RESOURCE_TYPE,
          "/data/type",
          "Resource identifier requires type");
    }
  }

  /** Whether {@code id} is present; empty and whitespace values still count as present. */
  public boolean hasId() {
    return id != null;
  }

  /** Whether {@code lid} is present; empty and whitespace values still count as present. */
  public boolean hasLid() {
    return lid != null;
  }

  /** Returns the {@code id} identity when present, otherwise the required {@code lid} identity. */
  public ResourceIdentity identityKey() {
    if (hasId()) {
      return ResourceIdentity.ofId(type, Objects.requireNonNull(id));
    }
    return ResourceIdentity.ofLid(type, Objects.requireNonNull(lid));
  }
}
