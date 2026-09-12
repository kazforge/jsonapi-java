package com.kazforge.jsonapi.jackson.representation;

import java.util.Objects;

/**
 * Immutable application policy governing an explicitly requested representation selection.
 *
 * <p>Defaults deny include traversal, allow every selected sparse field, limit include depth to 10,
 * and limit included resources to 100.
 *
 * <p>This is representation policy, not a complete authorization, persistence, endpoint-security,
 * or query policy.
 */
public record RepresentationPolicy(
    IncludePolicy includePolicy,
    FieldPolicy fieldPolicy,
    int maxIncludeDepth,
    int maxIncludedResources) {

  private static final int DEFAULT_MAX_INCLUDE_DEPTH = 10;
  private static final int DEFAULT_MAX_INCLUDED_RESOURCES = 100;
  private static final RepresentationPolicy DEFAULTS =
      new RepresentationPolicy(
          IncludePolicy.denyAll(),
          FieldPolicy.allowAll(),
          DEFAULT_MAX_INCLUDE_DEPTH,
          DEFAULT_MAX_INCLUDED_RESOURCES);

  public RepresentationPolicy {
    Objects.requireNonNull(includePolicy, "includePolicy");
    Objects.requireNonNull(fieldPolicy, "fieldPolicy");
    if (maxIncludeDepth < 0) {
      throw new IllegalArgumentException(
          "maxIncludeDepth must not be negative: " + maxIncludeDepth);
    }
    if (maxIncludedResources < 0) {
      throw new IllegalArgumentException(
          "maxIncludedResources must not be negative: " + maxIncludedResources);
    }
  }

  /** Returns the default include and sparse-fieldset policy. */
  public static RepresentationPolicy defaults() {
    return DEFAULTS;
  }

  public RepresentationPolicy withIncludePolicy(IncludePolicy policy) {
    return new RepresentationPolicy(policy, fieldPolicy, maxIncludeDepth, maxIncludedResources);
  }

  public RepresentationPolicy withFieldPolicy(FieldPolicy policy) {
    return new RepresentationPolicy(includePolicy, policy, maxIncludeDepth, maxIncludedResources);
  }

  public RepresentationPolicy withMaxIncludeDepth(int depth) {
    return new RepresentationPolicy(includePolicy, fieldPolicy, depth, maxIncludedResources);
  }

  public RepresentationPolicy withMaxIncludedResources(int count) {
    return new RepresentationPolicy(includePolicy, fieldPolicy, maxIncludeDepth, count);
  }
}
