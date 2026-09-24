package com.kazforge.jsonapi.jackson3.internal.codec;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/** Shared JSON object member iteration for token-driven wire readers. */
final class WireObjectMembers {

  @FunctionalInterface
  interface MemberHandler {
    void accept(String name);
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
      JsonParser parser, JsonPointerAccumulator pointer, MemberHandler handler) {
    forEachMember(parser, pointer, name -> true, handler);
  }

  /**
   * Variant that duplicate-checks only relevant members. Ignored unknown members are tolerated,
   * including repeats; recognized duplicates report {@code DUPLICATE_MEMBER}.
   */
  static void forEachMember(
      JsonParser parser,
      JsonPointerAccumulator pointer,
      Predicate<String> duplicateRelevant,
      MemberHandler handler) {
    WireTokens.expectToken(parser, JsonToken.START_OBJECT, pointer);
    pointer.capture(ReadLocations.token(parser));
    Set<String> seen = new HashSet<>();
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      String name = WireTokens.requireFieldName(parser, pointer);
      if (duplicateRelevant.test(name)) {
        WireTokens.rememberMember(seen, name, pointer, parser);
      }
      pointer.push(name);
      parser.nextToken();
      pointer.capture(ReadLocations.token(parser));
      handler.accept(name);
      pointer.pop();
    }
  }
}
