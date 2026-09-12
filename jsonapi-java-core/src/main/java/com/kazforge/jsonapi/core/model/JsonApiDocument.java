package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Top-level JSON:API document.
 *
 * <p>Components use Java {@code null} for absent members. Primary {@code data} uses {@link
 * DocumentData} so explicit JSON {@code null} remains distinct from absence. Construction enforces
 * local top-level rules ({@code data}/{@code errors} exclusivity, {@code included} only with {@code
 * data}, and at least one of {@code data}, {@code errors}, {@code meta}, or an extension member).
 *
 * <p>Cross-document rules (identity uniqueness, full linkage, extension/profile policy, and
 * similar) require {@link com.kazforge.jsonapi.core.validation.JsonApiDocumentValidator}.
 */
public record JsonApiDocument(
    @Nullable DocumentData data,
    @Nullable List<ErrorObject> errors,
    @Nullable Meta meta,
    @Nullable JsonApiObject jsonapi,
    @Nullable Links links,
    @Nullable List<ResourceObject> included,
    Map<String, @Nullable Object> additionalMembers) {

  private static final Set<String> RESERVED_ADDITIONAL =
      Set.of(
          JsonApiMembers.DATA,
          JsonApiMembers.ERRORS,
          JsonApiMembers.META,
          JsonApiMembers.JSONAPI,
          JsonApiMembers.LINKS,
          JsonApiMembers.INCLUDED);

  public JsonApiDocument {
    if (data != null && errors != null) {
      LocalValidation.fail(
          ValidationRuleCode.DATA_ERRORS_COEXIST, "", "data and errors must not coexist");
    }
    if (data == null && included != null) {
      LocalValidation.fail(
          ValidationRuleCode.INCLUDED_WITHOUT_DATA,
          "/included",
          "included must not be present without data");
    }
    if (errors != null) {
      errors = LocalValidation.copyRequiredList(errors, "/errors");
    }
    if (included != null) {
      included = LocalValidation.copyRequiredList(included, "/included");
    }
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers, "", "Invalid document member name: ", RESERVED_ADDITIONAL);
    boolean hasTopLevelMember =
        data != null || errors != null || meta != null || hasExtensionMembers(additionalMembers);
    if (!hasTopLevelMember) {
      LocalValidation.fail(
          ValidationRuleCode.MISSING_TOP_LEVEL_MEMBER,
          "",
          "Document must contain at least one of data, errors, meta, or extension members");
    }
  }

  public static JsonApiDocument withData(DocumentData data) {
    return new JsonApiDocument(data, null, null, null, null, null, Map.of());
  }

  /**
   * Creates a document with one error object in its present {@code errors} member.
   *
   * <p>Delegates to {@link #withErrors(List)} so error-list validation and snapshotting remain in
   * one construction path.
   */
  public static JsonApiDocument withError(ErrorObject error) {
    return withErrors(Collections.singletonList(error));
  }

  /**
   * Creates a document with a present {@code errors} member, including a valid present-empty array.
   *
   * <p>The document constructor validates and snapshots the supplied list.
   */
  public static JsonApiDocument withErrors(List<ErrorObject> errors) {
    return new JsonApiDocument(null, errors, null, null, null, null, Map.of());
  }

  public static JsonApiDocument withMeta(Meta meta) {
    return new JsonApiDocument(null, null, meta, null, null, null, Map.of());
  }

  public boolean hasDataMember() {
    return data != null;
  }

  public boolean hasErrorsMember() {
    return errors != null;
  }

  public boolean hasIncludedMember() {
    return included != null;
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
}
