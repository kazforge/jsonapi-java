package io.github.kazemek.jsonapi.jackson2.internal;

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
final class MemberClassifier {

  private MemberClassifier() {}

  static boolean isAtMember(String name) {
    return !name.isEmpty() && name.charAt(0) == '@';
  }

  /**
   * True when the name contains a namespace separator (first {@code :} after a non-empty prefix).
   */
  static boolean isNamespacedMember(String name) {
    int colon = name.indexOf(':');
    return colon > 0;
  }

  static boolean isPassThroughAttributeOrRelationship(String name) {
    return isAtMember(name) || isNamespacedMember(name);
  }

  /**
   * Only {@code @} members use the links additional-members channel. Namespaced keys remain link
   * relations (unlike attributes/relationships).
   */
  static boolean isPassThroughLinkMember(String name) {
    return isAtMember(name);
  }
}
