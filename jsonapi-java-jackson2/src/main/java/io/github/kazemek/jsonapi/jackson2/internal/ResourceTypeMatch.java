package io.github.kazemek.jsonapi.jackson2.internal;

import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;

/**
 * Shared wire-type equality for binders that reject a resource object whose JSON:API {@code type}
 * does not match the target mapping. Callers that need a different diagnostic category or location
 * must not use this helper.
 */
final class ResourceTypeMatch {

  static final MappingLocation TYPE_LOCATION = MappingLocation.of("type");

  private ResourceTypeMatch() {}

  static void requireMatching(String expectedType, ResourceObject resource, Class<?> rawType) {
    if (!expectedType.equals(resource.type())) {
      throw new JsonApiMappingException(
          MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
          rawType,
          TYPE_LOCATION,
          "Resource object type '"
              + resource.type()
              + "' does not match expected type '"
              + expectedType
              + "'");
    }
  }
}
