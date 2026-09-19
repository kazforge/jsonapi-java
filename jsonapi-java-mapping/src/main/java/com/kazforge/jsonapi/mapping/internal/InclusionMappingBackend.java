package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Optional;

/**
 * Mapper-backend capabilities required by the shared compound-inclusion algorithm.
 *
 * <p>The type token {@code T} is deliberately opaque to the shared mapping domain. A backend may
 * use a Jackson {@code JavaType}, a Gson/reflective type token, or another representation without
 * leaking that representation into inclusion semantics.
 */
public interface InclusionMappingBackend<T> {

  /** Runtime class represented by the backend type token, used for diagnostics only. */
  Class<?> rawClass(T type);

  /** JSON:API resource type mapped for the supplied backend type token. */
  String resourceType(T type);

  /**
   * Resolves the declared domain type reached through one JSON:API relationship.
   *
   * @return empty when the relationship is unknown on the owning resource type
   */
  Optional<T> relatedType(T ownerType, String relationshipName, String dottedPath);

  /** Reads and normalizes related domain objects for one mapped relationship. */
  List<Object> relatedValues(Object domain, T ownerType, String relationshipName);

  /** Resolves the effective runtime type while retaining backend-specific generic information. */
  T effectiveType(Object domain, T declaredType);

  /** Extracts the JSON:API identity of a mapped domain object. */
  ResourceIdentifier identifier(Object domain, T type);

  /** Renders one domain object into the canonical JSON:API core resource model. */
  ResourceObject render(Object domain, T type, MappingRepresentation representation);

  /** Returns whether the domain object currently carries either an id or local-id value. */
  boolean hasIdentity(Object domain, T type);
}
