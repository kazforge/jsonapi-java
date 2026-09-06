package io.github.kazemek.jsonapi.jackson2.internal;

/** RFC 6901 JSON Pointer segment escaping shared by wire pointer helpers. */
final class PointerEscapes {

  private PointerEscapes() {}

  static String escape(String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }

  static String unescape(String segment) {
    return segment.replace("~1", "/").replace("~0", "~");
  }
}
