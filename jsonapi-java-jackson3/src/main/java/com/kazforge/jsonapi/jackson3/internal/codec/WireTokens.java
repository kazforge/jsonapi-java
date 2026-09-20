package com.kazforge.jsonapi.jackson3.internal.codec;

import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.SourceLocation;
import com.kazforge.jsonapi.internal.wire.JsonPointerAccumulator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/** Token-level primitives shared by codec wire readers and open-value decoding. */
final class WireTokens {

  private WireTokens() {}

  static List<String> readStringArray(JsonParser parser, JsonPointerAccumulator pointer) {
    expectToken(parser, JsonToken.START_ARRAY, pointer);
    List<String> values = new ArrayList<>();
    int index = 0;
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      pointer.pushIndex(index);
      pointer.capture(ReadLocations.token(parser));
      values.add(readRequiredString(parser, pointer));
      pointer.pop();
      index++;
    }
    return List.copyOf(values);
  }

  static Number readNumber(JsonParser parser, JsonPointerAccumulator pointer) {
    JsonToken token = parser.currentToken();
    if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
      throw unexpectedToken(token, "number", pointer, parser);
    }
    return switch (parser.getNumberType()) {
      case INT -> parser.getIntValue();
      case LONG -> parser.getLongValue();
      case BIG_INTEGER -> parser.getBigIntegerValue();
      case FLOAT -> parser.getFloatValue();
      case DOUBLE -> parser.getDoubleValue();
      case BIG_DECIMAL -> parser.getDecimalValue();
      case null -> throw unexpectedToken(token, "number", pointer, parser);
    };
  }

  static String readRequiredString(JsonParser parser, JsonPointerAccumulator pointer) {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      throw unexpectedToken(parser.currentToken(), "string", pointer, parser);
    }
    return parser.getString();
  }

  static void skipValue(JsonParser parser) {
    parser.skipChildren();
  }

  static void expectToken(JsonParser parser, JsonToken expected, JsonPointerAccumulator pointer) {
    if (parser.currentToken() != expected) {
      throw unexpectedToken(parser.currentToken(), expected.name(), pointer, parser);
    }
  }

  static String requireFieldName(JsonParser parser, JsonPointerAccumulator pointer) {
    if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
      throw unexpectedToken(parser.currentToken(), "property name", pointer, parser);
    }
    String name = parser.currentName();
    if (name == null) {
      throw unexpected("Missing property name", pointer, parser);
    }
    return name;
  }

  static void rememberMember(
      Set<String> seen, String name, JsonPointerAccumulator pointer, JsonParser parser) {
    if (!seen.add(name)) {
      pointer.push(name);
      try {
        throw new JsonApiDocumentReadException(
            CodecFailureCategory.DUPLICATE_MEMBER,
            pointer.path(),
            ReadLocations.token(parser),
            "Duplicate object member");
      } finally {
        pointer.pop();
      }
    }
  }

  static JsonApiDocumentReadException unexpectedToken(
      @Nullable JsonToken actual,
      String expected,
      JsonPointerAccumulator pointer,
      JsonParser parser) {
    String actualName = actual == null ? "end-of-input" : actual.name();
    return new JsonApiDocumentReadException(
        CodecFailureCategory.UNEXPECTED_TOKEN,
        pointer.path(),
        locationForToken(parser, actual),
        "Unexpected token " + actualName + "; expected " + expected);
  }

  static JsonApiDocumentReadException unexpected(
      String message, JsonPointerAccumulator pointer, JsonParser parser) {
    return new JsonApiDocumentReadException(
        CodecFailureCategory.UNEXPECTED_TOKEN,
        pointer.path(),
        locationForToken(parser, parser.currentToken()),
        message);
  }

  private static SourceLocation locationForToken(JsonParser parser, @Nullable JsonToken token) {
    return token == null ? ReadLocations.current(parser) : ReadLocations.token(parser);
  }

  @SuppressWarnings("NullAway") // core type-use @Nullable Map values not visible across jars
  static Map<String, @Nullable Object> newNullableMap() {
    return new LinkedHashMap<>();
  }

  @SuppressWarnings("NullAway")
  static Map<String, @Nullable Relationship> newNullableRelationshipMap() {
    return new LinkedHashMap<>();
  }

  @SuppressWarnings("NullAway")
  static Map<String, @Nullable Link> newNullableLinkMap() {
    return new LinkedHashMap<>();
  }

  @SuppressWarnings("NullAway")
  static void putOpen(Map<String, @Nullable Object> map, String name, @Nullable Object value) {
    map.put(name, value);
  }

  @SuppressWarnings("NullAway")
  static void putRelationship(
      Map<String, @Nullable Relationship> map, String name, Relationship value) {
    map.put(name, value);
  }

  @SuppressWarnings("NullAway")
  static void putLink(Map<String, @Nullable Link> map, String name, @Nullable Link value) {
    map.put(name, value);
  }
}
