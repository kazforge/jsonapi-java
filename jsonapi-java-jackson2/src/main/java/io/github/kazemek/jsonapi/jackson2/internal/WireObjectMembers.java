package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Shared JSON object member iteration for token-driven wire readers. */
final class WireObjectMembers {

  @FunctionalInterface
  interface MemberHandler {
    void accept(String name) throws IOException;
  }

  private WireObjectMembers() {}

  /**
   * Expects {@link JsonToken#START_OBJECT}, captures the current pointer location, then invokes
   * {@code handler} once per member with the parser positioned on that member's value token.
   *
   * <p>Each handler must consume the complete member value, including nested arrays and objects, so
   * the parser rests on the last token of that value when the handler returns.
   */
  static void forEachMember(
      JsonParser parser, JsonPointerAccumulator pointer, MemberHandler handler) throws IOException {
    WireTokens.expectToken(parser, JsonToken.START_OBJECT, pointer);
    pointer.capture(parser);
    Set<String> seen = new HashSet<>();
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      String name = WireTokens.requireFieldName(parser, pointer);
      WireTokens.rememberMember(seen, name, pointer, parser);
      pointer.push(name);
      parser.nextToken();
      pointer.capture(parser);
      handler.accept(name);
      pointer.pop();
    }
  }
}
