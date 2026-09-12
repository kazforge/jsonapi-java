package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.internal.AdditionalMembers;
import com.kazforge.jsonapi.core.internal.SyntaxValidators;
import com.kazforge.jsonapi.core.validation.LocalValidation;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A JSON:API link as a URI reference string or link object.
 *
 * @apiNote {@link StringLink} is the string form (URI reference). {@link ObjectLink} requires
 *     {@code href} and may carry {@code rel}, {@code describedby}, {@code title}, {@code type},
 *     {@code hreflang}, {@code meta}, and additional members. JSON:API permits a link to be an
 *     explicit {@code null}; that state shares this model's {@code null} absence representation, so
 *     an omitted or explicitly-null {@code describedby} is {@code null}. When present it is another
 *     {@link Link} in string or object form. {@code hreflang} is modeled as a list; codec emission
 *     of single vs array forms is deferred to the Jackson module.
 */
public sealed interface Link permits Link.StringLink, Link.ObjectLink {

  record StringLink(String href) implements Link {
    public StringLink {
      requireValidHref(href, path());
    }
  }

  record ObjectLink(
      String href,
      @Nullable String rel,
      @Nullable Link describedby,
      @Nullable String title,
      @Nullable String type,
      @Nullable List<String> hreflang,
      @Nullable Meta meta,
      Map<String, @Nullable Object> additionalMembers)
      implements Link {

    private static final Set<String> RESERVED_ADDITIONAL =
        Set.of(
            JsonApiMembers.HREF,
            JsonApiMembers.REL,
            JsonApiMembers.DESCRIBEDBY,
            JsonApiMembers.TITLE,
            JsonApiMembers.TYPE,
            JsonApiMembers.HREFLANG,
            JsonApiMembers.META);

    public ObjectLink {
      requireValidHref(href, path());
      if (rel != null && !SyntaxValidators.isValidLinkRelation(rel)) {
        LocalValidation.fail(
            ValidationRuleCode.INVALID_LINK_RELATION,
            path() + "/rel",
            "Invalid link relation: " + rel);
      }
      if (type != null && !SyntaxValidators.isValidMediaType(type)) {
        LocalValidation.fail(
            ValidationRuleCode.INVALID_MEDIA_TYPE, path() + "/type", "Invalid media type: " + type);
      }
      if (hreflang != null) {
        for (int i = 0; i < hreflang.size(); i++) {
          String tag = hreflang.get(i);
          if (!SyntaxValidators.isValidLanguageTag(tag)) {
            LocalValidation.fail(
                ValidationRuleCode.INVALID_LANGUAGE_TAG,
                path() + "/hreflang/" + i,
                "Invalid language tag: " + tag);
          }
        }
        hreflang = List.copyOf(hreflang);
      }
      additionalMembers =
          AdditionalMembers.copy(
              additionalMembers, path(), "Invalid link member name: ", RESERVED_ADDITIONAL);
    }

    public static ObjectLink ofHref(String href) {
      return new ObjectLink(href, null, null, null, null, null, null, Map.of());
    }

    public static ObjectLink withHreflang(String href, List<String> hreflang) {
      return new ObjectLink(href, null, null, null, null, hreflang, null, Map.of());
    }

    public static ObjectLink withHreflang(String href, String singleLanguage) {
      return new ObjectLink(href, null, null, null, null, List.of(singleLanguage), null, Map.of());
    }
  }

  private static void requireValidHref(String href, String path) {
    if (!SyntaxValidators.isValidUriReference(href)) {
      LocalValidation.fail(
          ValidationRuleCode.INVALID_URI_REFERENCE, path + "/href", "Invalid link href: " + href);
    }
  }

  private static String path() {
    return "/links";
  }
}
