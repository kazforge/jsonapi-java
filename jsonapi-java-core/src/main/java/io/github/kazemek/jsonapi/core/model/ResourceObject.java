package io.github.kazemek.jsonapi.core.model;

import io.github.kazemek.jsonapi.core.internal.AdditionalMembers;
import io.github.kazemek.jsonapi.core.internal.JsonPointers;
import io.github.kazemek.jsonapi.core.validation.LocalValidation;
import io.github.kazemek.jsonapi.core.validation.MemberNames;
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A JSON:API resource object.
 *
 * <p>Attributes and relationships share one field namespace. Semantic members and non-{@code @}
 * pass-through members cannot use the same name; {@code @}-prefixed pass-through members remain
 * outside that namespace. Extension and profile authorization remains aggregate-validator policy.
 */
public record ResourceObject(
    String type,
    @Nullable String id,
    @Nullable String lid,
    @Nullable Attributes attributes,
    @Nullable Relationships relationships,
    @Nullable Links links,
    @Nullable Meta meta,
    Map<String, @Nullable Object> additionalMembers) {

  public ResourceObject {
    requireType(type);
    if (!MemberNames.isValid(type)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_MEMBER_NAME, "/data/type", "Invalid resource type: " + type);
    }
    validateFieldNamespace(attributes, relationships);
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers,
            "/data",
            "Invalid resource member name: ",
            Set.of(
                JsonApiMembers.TYPE,
                JsonApiMembers.ID,
                JsonApiMembers.LID,
                JsonApiMembers.ATTRIBUTES,
                JsonApiMembers.RELATIONSHIPS,
                JsonApiMembers.LINKS,
                JsonApiMembers.META));
  }

  public static ResourceObject ofType(String type) {
    return new ResourceObject(type, null, null, null, null, null, null, Map.of());
  }

  public static ResourceObject of(String type, String id) {
    return new ResourceObject(type, id, null, null, null, null, null, Map.of());
  }

  public boolean hasId() {
    return id != null;
  }

  public boolean hasLid() {
    return lid != null;
  }

  public @Nullable ResourceIdentity identityKey() {
    if (hasId()) {
      return ResourceIdentity.ofId(type, Objects.requireNonNull(id));
    }
    if (hasLid()) {
      return ResourceIdentity.ofLid(type, Objects.requireNonNull(lid));
    }
    return null;
  }

  // NullAway misreads ResourceIdentifier's type-use @Nullable on class-path inputs during
  // incremental compiles; the component types are identical and the conversion is safe.
  @SuppressWarnings("NullAway")
  public ResourceIdentifier toIdentifier() {
    return new ResourceIdentifier(type, id, lid, meta, additionalMembers);
  }

  private static void requireType(@Nullable String type) {
    if (type == null) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_RESOURCE_TYPE, "/data/type", "Resource object requires type");
    }
  }

  private static void validateFieldNamespace(
      @Nullable Attributes attributes, @Nullable Relationships relationships) {
    if (attributes == null || relationships == null) {
      return;
    }
    for (String name : attributes.attributes().keySet()) {
      validateAttributeFieldName(name, relationships);
    }
    for (String name : attributes.additionalMembers().keySet()) {
      validateAttributeFieldName(name, relationships);
    }
  }

  private static void validateAttributeFieldName(String name, Relationships relationships) {
    if (MemberNames.isAtMember(name)) {
      return;
    }
    if (relationships.relationships().containsKey(name)
        || relationships.additionalMembers().containsKey(name)) {
      LocalValidation.fail(
          ValidationRuleCode.MEMBER_NAME_COLLISION,
          JsonPointers.child("/data/relationships", name),
          "Attribute and relationship name collision: " + name);
    }
  }
}
