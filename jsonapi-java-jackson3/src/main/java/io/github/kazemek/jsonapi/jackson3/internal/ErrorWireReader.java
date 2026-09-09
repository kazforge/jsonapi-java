package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.core.model.ErrorObject;
import io.github.kazemek.jsonapi.core.model.ErrorSource;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.Links;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.jackson.internal.wire.JsonPointerAccumulator;
import io.github.kazemek.jsonapi.jackson.internal.wire.ValidationPointers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/** Error object and error-source decoding. */
final class ErrorWireReader {

  private static final Set<String> ERROR_MEMBERS =
      Set.of(
          JsonApiMembers.ID,
          JsonApiMembers.LINKS,
          JsonApiMembers.STATUS,
          JsonApiMembers.CODE,
          JsonApiMembers.TITLE,
          JsonApiMembers.DETAIL,
          JsonApiMembers.SOURCE,
          JsonApiMembers.META);

  private static final Set<String> ERROR_SOURCE_MEMBERS =
      Set.of(JsonApiMembers.POINTER, JsonApiMembers.PARAMETER, JsonApiMembers.HEADER);

  private ErrorWireReader() {}

  static List<ErrorObject> readErrorObjects(JsonParser parser, JsonPointerAccumulator pointer) {
    WireTokens.expectToken(parser, JsonToken.START_ARRAY, pointer);
    List<ErrorObject> errors = new ArrayList<>();
    int index = 0;
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      pointer.pushIndex(index);
      pointer.capture(ReadLocations.token(parser));
      errors.add(readErrorObject(parser, pointer));
      pointer.pop();
      index++;
    }
    return List.copyOf(errors);
  }

  static ErrorObject readErrorObject(JsonParser parser, JsonPointerAccumulator pointer) {
    ErrorDraft draft = new ErrorDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  static ErrorSource readErrorSource(JsonParser parser, JsonPointerAccumulator pointer) {
    ErrorSourceDraft draft = new ErrorSourceDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  private static final class ErrorDraft {
    private @Nullable String id;
    private @Nullable Links links;
    private @Nullable String status;
    private @Nullable String code;
    private @Nullable String title;
    private @Nullable String detail;
    private @Nullable ErrorSource source;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.ID -> id = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.LINKS -> links = LinkWireReader.readLinks(parser, pointer);
        case JsonApiMembers.STATUS -> status = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.CODE -> code = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.TITLE -> title = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.DETAIL -> detail = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.SOURCE -> source = readErrorSource(parser, pointer);
        case JsonApiMembers.META -> meta = DocumentWireReader.readMeta(parser, pointer);
        default -> {
          if (ERROR_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected error member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    ErrorObject build(JsonPointerAccumulator pointer) {
      String errorId = id;
      Links errorLinks = links;
      String errorStatus = status;
      String errorCode = code;
      String errorTitle = title;
      String errorDetail = detail;
      ErrorSource errorSource = source;
      Meta errorMeta = meta;
      return ValidationPointers.construct(
          pointer.path(),
          "/errors",
          () ->
              new ErrorObject(
                  errorId,
                  errorLinks,
                  errorStatus,
                  errorCode,
                  errorTitle,
                  errorDetail,
                  errorSource,
                  errorMeta,
                  ValidationPointers.forCore(additional)));
    }
  }

  private static final class ErrorSourceDraft {
    private @Nullable String pointerValue;
    private @Nullable String parameter;
    private @Nullable String header;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.POINTER ->
            pointerValue = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.PARAMETER -> parameter = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.HEADER -> header = WireTokens.readRequiredString(parser, pointer);
        default -> {
          if (ERROR_SOURCE_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected error source member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    ErrorSource build(JsonPointerAccumulator pointer) {
      String sourcePointer = pointerValue;
      String sourceParameter = parameter;
      String sourceHeader = header;
      return ValidationPointers.construct(
          pointer.path(),
          "/errors/source",
          () ->
              new ErrorSource(
                  sourcePointer,
                  sourceParameter,
                  sourceHeader,
                  ValidationPointers.forCore(additional)));
    }
  }
}
