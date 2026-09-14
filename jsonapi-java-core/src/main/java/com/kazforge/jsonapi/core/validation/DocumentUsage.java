package com.kazforge.jsonapi.core.validation;

/** Declares the document operation for context-sensitive validation. */
public enum DocumentUsage {
  /**
   * Base-spec create-resource operation: the validator applies single-resource primary-data shape
   * and primary relationship-data requirements only when composed with an ordinary resource
   * endpoint role (see {@link PrimaryDataContext#RESOURCE}) and a primary-data resource occurrence.
   * Under the relationship endpoint role the operation adds no resource-shape rules. Create
   * identity leniency (an omittable resource {@code id} on the primary create resource, with {@code
   * id} and {@code lid} staying independent) applies only to that primary resource occurrence; a
   * {@code lid}-only relationship identifier is accepted only as a self-reference to the same
   * primary resource (matching {@code type} and {@code lid}), while unrelated linkage and included
   * resources require {@code id}. Core itself is not HTTP-method-aware; a future server layer
   * selects this usage from its own operation context.
   */
  CREATE_REQUEST,
  /**
   * Base-spec update operation: the validator applies single-resource primary-data shape, primary
   * relationship-data requirements, and expected endpoint-identity comparison only when composed
   * with an ordinary resource endpoint role (see {@link PrimaryDataContext#RESOURCE}) and a
   * primary-data resource occurrence. Under the relationship endpoint role the operation adds no
   * resource-shape rules.
   */
  UPDATE_REQUEST,
  /** Response or any other document use. */
  RESPONSE_OR_OTHER
}
