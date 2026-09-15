package com.kazforge.jsonapi.jackson2.internal.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.jackson.internal.wire.JsonPointerAccumulator;
import com.kazforge.jsonapi.jackson.internal.wire.ValidationPointers;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shared meta-object decoding for document, resource, error, and link wire readers.
 *
 * <p>Owned by the codec so nested readers decode meta without calling back into the top-level
 * document orchestrator.
 */
final class WireMetaReader {

  private WireMetaReader() {}

  static Meta readMeta(JsonParser parser, JsonPointerAccumulator pointer) throws IOException {
    Map<String, @Nullable Object> members = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name -> WireTokens.putOpen(members, name, WireOpenValues.readOpenValue(parser, pointer)));
    return ValidationPointers.construct(
        pointer.path(), "/meta", () -> Meta.of(ValidationPointers.forCore(members)));
  }
}
