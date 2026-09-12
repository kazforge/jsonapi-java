package com.kazforge.jsonapi.core.validation;

import org.jspecify.annotations.Nullable;

/** JSON:API v1.1 member-name grammar validation. */
public final class MemberNames {

  private MemberNames() {}

  public static boolean isValid(@Nullable String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    if (isAtMember(name)) {
      return isValidImplementationMember(name.substring(1));
    }
    int colon = name.indexOf(':');
    if (colon == 0) {
      return false;
    }
    if (colon > 0) {
      return isValidExtensionMember(name, colon);
    }
    return isValidImplementationMember(name);
  }

  public static boolean isExtensionMember(@Nullable String name) {
    if (name == null) {
      return false;
    }
    int colon = name.indexOf(':');
    return colon > 0 && isValidExtensionMember(name, colon);
  }

  public static boolean isAtMember(@Nullable String name) {
    return name != null && !name.isEmpty() && name.charAt(0) == '@';
  }

  private static boolean isValidExtensionMember(String name, int colon) {
    if (name.indexOf(':', colon + 1) >= 0) {
      return false;
    }
    return isValidExtensionNamespace(name.substring(0, colon))
        && isValidImplementationMember(name.substring(colon + 1));
  }

  private static boolean isValidExtensionNamespace(String namespace) {
    if (namespace.isEmpty()) {
      return false;
    }
    for (int i = 0; i < namespace.length(); i++) {
      char c = namespace.charAt(i);
      if (isNotAlpha(c) && isNotDigit(c)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidImplementationMember(String name) {
    int length = name.length();
    if (length == 0) {
      return false;
    }
    if (isNotMemberEdge(name.charAt(0)) || isNotMemberEdge(name.charAt(length - 1))) {
      return false;
    }
    for (int i = 1; i < length - 1; i++) {
      char c = name.charAt(i);
      if (isNotMemberEdge(c) && c != '-' && c != '_' && c != ' ') {
        return false;
      }
    }
    return true;
  }

  private static boolean isNotMemberEdge(char c) {
    return isNotAlpha(c) && isNotDigit(c) && c <= 0x7F;
  }

  private static boolean isNotAlpha(char c) {
    return !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
  }

  private static boolean isNotDigit(char c) {
    return !(c >= '0' && c <= '9');
  }
}
