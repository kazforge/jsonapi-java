package com.kazforge.jsonapi.jackson.internal.wire;

/** RFC 6901 JSON Pointer segment escaping shared by wire pointer helpers. */
public final class PointerEscapes {

  private PointerEscapes() {}

  public static String escape(String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }

  public static String unescape(String segment) {
    return segment.replace("~1", "/").replace("~0", "~");
  }
}
