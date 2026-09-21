package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;

/**
 * Backend capability boundary required by the shared compound-inclusion engine.
 *
 * <p>The type token {@code T} is deliberately opaque to the shared mapping domain: a backend may
 * use a Jackson {@code JavaType} or another native type representation without leaking it into
 * inclusion semantics. Implementations are backend-local bridges over native type tokens, mapping
 * definitions, property access, and selective rendering. This is unsupported implementation detail
 * for backend cooperation, not consumer SPI, and must not appear in supported backend signatures.
 *
 * <p>Callers must ask {@link #hasRelationship} before {@link #relatedType} or {@link
 * #relatedDomainObjects}; implementations may reject a name they consider unknown with {@link
 * IllegalArgumentException}. Native type/container resolution failures that cannot be expressed in
 * neutral terms surface as the backend's own {@code JsonApiMappingException} diagnostic.
 */
public interface InclusionBackend<T> {

  /** Runtime class represented by the backend type token, used for diagnostics only. */
  Class<?> rawClass(T type);

  /** JSON:API resource type mapped for the supplied backend type token. */
  String resourceType(T type);

  /** Returns whether the mapped owner type declares a relationship with this JSON:API name. */
  boolean hasRelationship(T ownerType, String relationshipName);

  /**
   * Resolves the declared domain type reached through one known JSON:API relationship, after
   * unwrapping native transport wrappers, collection content, and relationship-linkage targets.
   *
   * @param dottedPath include path through this relationship, for backend resolution diagnostics
   */
  T relatedType(T ownerType, String relationshipName, String dottedPath);

  /**
   * Reads and normalizes related domain objects for one known JSON:API relationship, excluding
   * non-includable values such as identifiers, linkage data, or nulls.
   */
  List<Object> relatedDomainObjects(Object domain, T ownerType, String relationshipName);

  /** Resolves the effective runtime type while retaining backend-native generic information. */
  T effectiveType(Object domain, T declaredType);

  /** Extracts the JSON:API identity of a mapped domain object. */
  ResourceIdentifier identifier(Object domain, T type);

  /** Renders one domain object into the canonical JSON:API core resource model. */
  ResourceObject render(Object domain, T type, EffectiveRepresentation representation);

  /** Returns whether the domain object currently carries either an id or local-id value. */
  boolean hasIdentity(Object domain, T type);
}
