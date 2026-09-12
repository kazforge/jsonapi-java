package com.kazforge.jsonapi.jackson.representation;

import java.util.Objects;

/**
 * Allowance key for include-policy checks: owner JSON:API resource type plus relationship name.
 *
 * <p>{@code resourceType} is the JSON:API type of the relationship <em>owner</em>, not the target
 * type and not a Java class name. Matched at every nested include-path segment.
 */
public record RelationshipAllowance(String resourceType, String relationshipName) {

  public RelationshipAllowance {
    Objects.requireNonNull(resourceType, "resourceType");
    Objects.requireNonNull(relationshipName, "relationshipName");
  }

  public static RelationshipAllowance of(String resourceType, String relationshipName) {
    return new RelationshipAllowance(resourceType, relationshipName);
  }
}
