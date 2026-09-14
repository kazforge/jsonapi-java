package com.kazforge.jsonapi.jackson.internal.wire;

import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.validation.LinksContext;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.core.validation.ValidationContext;

/**
 * Structural classification of JSON:API member names without depending on {@code core.internal}.
 *
 * <p>For attributes and relationships, {@code @} members and namespaced {@code namespace:name}
 * members are pass-through (open JSON in {@code additionalMembers}).
 *
 * <p>For links objects the split differs on purpose and matches core {@code Links}: only {@code @}
 * members are pass-through. Namespaced keys are extension link relations and decode as {@code Link}
 * values in the semantic links map (namespace policy is enforced during aggregate validation).
 *
 * <p>Readers additionally use the bound {@link ValidationContext} to decide whether an
 * otherwise-unrecognized structural member is retained ({@code @}, valid extension, or explicitly
 * allowed profile member) or discarded as unknown, and whether a links-object key is recognized for
 * its {@link LinksContext}.
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

  /**
   * True when an otherwise-unrecognized structural member must be retained on read: {@code @},
   * valid extension, or explicitly allowed profile member. Every other name is unknown and
   * discarded by token-driven readers before model construction.
   */
  public static boolean isRetainedStructuralMember(String name, ValidationContext context) {
    if (isAtMember(name) || MemberNames.isExtensionMember(name)) {
      return true;
    }
    return context.allowedProfileMemberNames().contains(name);
  }

  /**
   * True when a links-object key is recognized for its {@link LinksContext}: {@code @}
   * pass-through, valid extension relation, allowed profile relation, or context-standard relation.
   * Every other key is unknown and discarded on read without interpreting its value as a link.
   */
  public static boolean isRecognizedLinkMember(
      String name, ValidationContext context, LinksContext linksContext) {
    if (isAtMember(name) || MemberNames.isExtensionMember(name)) {
      return true;
    }
    if (context.allowedProfileMemberNames().contains(name)) {
      return true;
    }
    return Links.standardMembers(linksContext).contains(name);
  }
}
