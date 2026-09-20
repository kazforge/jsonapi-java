package com.kazforge.jsonapi.jackson2.internal.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.kazforge.jsonapi.internal.wire.JsonPointerAccumulator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Recursive open JSON value decoding shared by codec wire readers.
 *
 * <p>Depends on token primitives and object-member iteration; token primitives never depend back on
 * open-value decoding, and member iteration never depends on open values.
 */
final class WireOpenValues {

  private WireOpenValues() {}

  static @Nullable Object readOpenValue(JsonParser parser, JsonPointerAccumulator pointer)
      throws IOException {
    JsonToken token = parser.currentToken();
    if (token == null) {
      throw WireTokens.unexpected("Expected a JSON value", pointer, parser);
    }
    return switch (token) {
      case VALUE_NULL -> null;
      case VALUE_STRING -> parser.getText();
      case VALUE_TRUE, VALUE_FALSE -> parser.getBooleanValue();
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> WireTokens.readNumber(parser, pointer);
      case START_ARRAY -> readOpenArray(parser, pointer);
      case START_OBJECT -> readOpenObject(parser, pointer);
      default -> throw WireTokens.unexpectedToken(token, "a JSON value", pointer, parser);
    };
  }

  static List<@Nullable Object> readOpenArray(JsonParser parser, JsonPointerAccumulator pointer)
      throws IOException {
    WireTokens.expectToken(parser, JsonToken.START_ARRAY, pointer);
    List<@Nullable Object> values = new ArrayList<>();
    int index = 0;
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      pointer.pushIndex(index);
      pointer.capture(ReadLocations.token(parser));
      values.add(readOpenValue(parser, pointer));
      pointer.pop();
      index++;
    }
    // Open arrays may contain JSON null; List.copyOf would reject null elements.
    return Collections.unmodifiableList(values);
  }

  static Map<String, @Nullable Object> readOpenObject(
      JsonParser parser, JsonPointerAccumulator pointer) throws IOException {
    Map<String, @Nullable Object> values = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> WireTokens.putOpen(values, name, readOpenValue(parser, pointer)));
    return values;
  }
}
