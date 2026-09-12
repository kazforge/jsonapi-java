package com.kazforge.jsonapi.jackson.internal.wire;

/**
 * Structural classification of JSON:API member names without depending on {@code core.internal}.
 *
 * <p>For attributes and relationships, {@code @} members and namespaced {@code namespace:name}
 * members are pass-through (open JSON in {@code additionalMembers}).
 *
 * <p>For links objects the split differs on purpose and matches core {@code Links}: only {@code @}
 * members are pass-through. Namespaced keys are extension link relations and decode as {@code Link}
 * values in the semantic links map (namespace policy is enforced during aggregate validation).
 */
public final class MemberClassifier {

  private MemberClassifier() {}

  public static boolean isAtMember(String name) {
    return !name.isEmpty() && name.charAt(0) == '@';
  }

  /** True when the name contains a namespace separator after a non-empty prefix. */
  public static boolean isNamespacedMember(String name) {
    int colon = name.indexOf(':');
    return colon > 0;
  }

  public static boolean isPassThroughAttributeOrRelationship(String name) {
    return isAtMember(name) || isNamespacedMember(name);
  }

  /** Only {@code @} members use the links additional-members channel. */
  public static boolean isPassThroughLinkMember(String name) {
    return isAtMember(name);
  }
}
