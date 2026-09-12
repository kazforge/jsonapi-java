package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.internal.SyntaxValidators;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Top-level {@code jsonapi} object. */
public record JsonApiObject(
    @Nullable String version,
    @Nullable List<String> ext,
    @Nullable List<String> profile,
    @Nullable Meta meta,
    Map<String, @Nullable Object> additionalMembers) {

  private static final Set<String> RESERVED_ADDITIONAL =
      Set.of(
          JsonApiMembers.VERSION, JsonApiMembers.EXT, JsonApiMembers.PROFILE, JsonApiMembers.META);

  public JsonApiObject {
    if (ext != null) {
      for (int i = 0; i < ext.size(); i++) {
        String uri = ext.get(i);
        if (!SyntaxValidators.isValidExtensionOrProfileUri(uri)) {
          LocalValidation.fail(
              ValidationRuleCode.INVALID_EXTENSION_URI,
              "/jsonapi/ext/" + i,
              "Invalid extension URI: " + uri);
        }
      }
      ext = List.copyOf(ext);
    }
    if (profile != null) {
      for (int i = 0; i < profile.size(); i++) {
        String uri = profile.get(i);
        if (!SyntaxValidators.isValidExtensionOrProfileUri(uri)) {
          LocalValidation.fail(
              ValidationRuleCode.INVALID_PROFILE_URI,
              "/jsonapi/profile/" + i,
              "Invalid profile URI: " + uri);
        }
      }
      profile = List.copyOf(profile);
    }
    additionalMembers =
        AdditionalMembers.copy(
            additionalMembers, "/jsonapi", "Invalid jsonapi member name: ", RESERVED_ADDITIONAL);
  }

  public static JsonApiObject ofVersion(String version) {
    return new JsonApiObject(version, null, null, null, Map.of());
  }
}
