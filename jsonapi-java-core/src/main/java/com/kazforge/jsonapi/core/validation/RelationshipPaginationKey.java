package com.kazforge.jsonapi.core.validation;

/** Occurrence key for relationship pagination hints: resource type plus relationship name. */
public record RelationshipPaginationKey(String resourceType, String relationshipName) {

  public RelationshipPaginationKey {
    resourceType =
        LocalValidation.requireNonNull(
            resourceType,
            "/relationshipPaginationHints",
            "Pagination key resource type must not be null");
    relationshipName =
        LocalValidation.requireNonNull(
            relationshipName,
            "/relationshipPaginationHints",
            "Pagination key relationship name must not be null");
  }

  public static RelationshipPaginationKey of(String resourceType, String relationshipName) {
    return new RelationshipPaginationKey(resourceType, relationshipName);
  }
}
