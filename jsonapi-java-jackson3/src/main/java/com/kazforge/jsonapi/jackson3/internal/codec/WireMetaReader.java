package com.kazforge.jsonapi.jackson3.internal.codec;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.jackson.internal.wire.JsonPointerAccumulator;
import com.kazforge.jsonapi.jackson.internal.wire.ValidationPointers;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;

/**
 * Shared meta-object decoding for document, resource, error, and link wire readers.
 *
 * <p>Owned by the codec so nested readers decode meta without calling back into the top-level
 * document orchestrator.
 */
final class WireMetaReader {

  private WireMetaReader() {}

  static Meta readMeta(JsonParser parser, JsonPointerAccumulator pointer) {
    Map<String, @Nullable Object> members = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name -> WireTokens.putOpen(members, name, WireOpenValues.readOpenValue(parser, pointer)));
    return ValidationPointers.construct(
        pointer.path(), "/meta", () -> Meta.of(ValidationPointers.forCore(members)));
  }
}
