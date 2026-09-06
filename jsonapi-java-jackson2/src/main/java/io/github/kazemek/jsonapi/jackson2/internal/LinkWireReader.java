package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.Link;
import io.github.kazemek.jsonapi.core.model.Links;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException;
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Links object and individual link decoding. */
final class LinkWireReader {

  private static final String PATH_LINKS = "/links";

  private static final Set<String> LINK_OBJECT_MEMBERS =
      Set.of(
          JsonApiMembers.HREF,
          JsonApiMembers.REL,
          JsonApiMembers.DESCRIBEDBY,
          JsonApiMembers.TITLE,
          JsonApiMembers.TYPE,
          JsonApiMembers.HREFLANG,
          JsonApiMembers.META);

  private LinkWireReader() {}

  static Links readLinks(JsonParser parser, JsonPointerAccumulator pointer) throws IOException {
    Map<String, @Nullable Link> links = WireTokens.newNullableLinkMap();
    Map<String, @Nullable Object> additional = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name -> {
          if (MemberClassifier.isPassThroughLinkMember(name)) {
            WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
          } else {
            WireTokens.putLink(links, name, readLink(parser, pointer));
          }
        });
    return ValidationPointers.construct(
        pointer, PATH_LINKS, () -> Links.of(links, ValidationPointers.forCore(additional)));
  }

  static @Nullable Link readLink(JsonParser parser, JsonPointerAccumulator pointer)
      throws IOException {
    JsonToken token = parser.currentToken();
    if (token == JsonToken.VALUE_NULL) {
      return null;
    }
    if (token == JsonToken.VALUE_STRING) {
      String href = parser.getText();
      return ValidationPointers.construct(pointer, PATH_LINKS, () -> new Link.StringLink(href));
    }
    if (token == JsonToken.START_OBJECT) {
      return readObjectLink(parser, pointer);
    }
    throw WireTokens.unexpectedToken(token, "null, string, or object for link", pointer, parser);
  }

  static Link.ObjectLink readObjectLink(JsonParser parser, JsonPointerAccumulator pointer)
      throws IOException {
    ObjectLinkDraft draft = new ObjectLinkDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  static List<String> readHreflang(JsonParser parser, JsonPointerAccumulator pointer)
      throws IOException {
    JsonToken token = parser.currentToken();
    if (token == JsonToken.VALUE_STRING) {
      return List.of(parser.getText());
    }
    if (token == JsonToken.START_ARRAY) {
      return WireTokens.readStringArray(parser, pointer);
    }
    throw WireTokens.unexpectedToken(token, "string or array for hreflang", pointer, parser);
  }

  private static final class ObjectLinkDraft {
    private @Nullable String href;
    private @Nullable String rel;
    private @Nullable String describedby;
    private @Nullable String title;
    private @Nullable String type;
    private @Nullable List<String> hreflang;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer)
        throws IOException {
      switch (name) {
        case JsonApiMembers.HREF -> href = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.REL -> rel = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.DESCRIBEDBY ->
            describedby = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.TITLE -> title = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.TYPE -> type = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.HREFLANG -> hreflang = readHreflang(parser, pointer);
        case JsonApiMembers.META -> meta = DocumentWireReader.readMeta(parser, pointer);
        default -> {
          if (LINK_OBJECT_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected link object member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    Link.ObjectLink build(JsonPointerAccumulator pointer) {
      if (href == null) {
        throw new JsonApiValidationException(
            ValidationRuleCode.NULL_REQUIRED_VALUE,
            pointer.path() + "/href",
            "Link object requires href");
      }
      String linkHref = href;
      String linkRel = rel;
      String linkDescribedby = describedby;
      String linkTitle = title;
      String linkType = type;
      List<String> linkHreflang = hreflang;
      Meta linkMeta = meta;
      return ValidationPointers.construct(
          pointer,
          PATH_LINKS,
          () ->
              new Link.ObjectLink(
                  linkHref,
                  linkRel,
                  linkDescribedby,
                  linkTitle,
                  linkType,
                  linkHreflang,
                  linkMeta,
                  ValidationPointers.forCore(additional)));
    }
  }
}
