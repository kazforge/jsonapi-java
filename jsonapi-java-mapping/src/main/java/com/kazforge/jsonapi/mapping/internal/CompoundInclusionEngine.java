package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.representation.IncludePath;
import com.kazforge.jsonapi.representation.IncludePolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral compound-inclusion semantics: pre-validates include paths and walks domain
 * graphs.
 *
 * <p>This engine knows nothing about a concrete backend. Native type tokens, mapping definitions,
 * property access, wrapper/collection unwrapping, conversion, and selective rendering stay behind
 * {@link InclusionBackend}. Traversal order, include policy, identity aliasing, deduplication,
 * sparse-fieldset omissions, limits, and diagnostics live here once for every backend.
 *
 * <p>All visit, identity, and included-output state is allocated per {@link #collectIncluded}
 * invocation. The engine instance itself is immutable and safe to share. Included resources are
 * emitted through the backend's fieldset-aware selective write path.
 *
 * @param <T> opaque backend-native type token
 */
public final class CompoundInclusionEngine<T> {

  private static final String INCLUDE_PATH_CONTEXT = " in include path '";

  private final InclusionBackend<T> backend;

  public CompoundInclusionEngine(InclusionBackend<T> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  /**
   * Collects included resources for the given primary domain snapshot and context.
   *
   * @return included list {@code null} when inclusion was not requested, an empty list for an
   *     explicit empty include request, plus the identities of included resources whose inbound
   *     linkage was removed by an applied fieldset while inclusion still traversed the linking
   *     relationship
   */
  public IncludedResourcesResult collectIncluded(
      List<?> primarySnapshot,
      List<T> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable T emptyPrimaryType,
      EffectiveRepresentation representation) {
    return collectIncluded(
        primarySnapshot, primaryTypes, primaryResources, emptyPrimaryType, representation, false);
  }

  /**
   * Collects included resources, additionally allowing identity-less primary roots for
   * create-request authoring: a primary domain object with neither {@code id} nor {@code lid} value
   * is traversed without a wire-identity visit key instead of failing. Related and included
   * resources still require identity wherever linkage semantics need it.
   *
   * @return included list {@code null} when inclusion was not requested, an empty list for an
   *     explicit empty include request, plus the identities of included resources whose inbound
   *     linkage was removed by an applied fieldset while inclusion still traversed the linking
   *     relationship
   */
  public IncludedResourcesResult collectIncluded(
      List<?> primarySnapshot,
      List<T> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable T emptyPrimaryType,
      EffectiveRepresentation representation,
      boolean allowIdentitylessRoots) {
    Objects.requireNonNull(primarySnapshot, "primarySnapshot");
    Objects.requireNonNull(primaryTypes, "primaryTypes");
    Objects.requireNonNull(primaryResources, "primaryResources");
    Objects.requireNonNull(representation, "representation");
    if (primarySnapshot.size() != primaryResources.size()
        || primarySnapshot.size() != primaryTypes.size()) {
      throw new IllegalArgumentException(
          "primary snapshot, type, and resource lists must match in size");
    }

    List<IncludePath> paths = representation.selection().includePaths();
    if (paths.isEmpty()) {
      return new IncludedResourcesResult(
          representation.selection().includeRequested() ? List.of() : null, Set.of());
    }

    List<T> validationTypes =
        primaryTypes.isEmpty() && emptyPrimaryType != null
            ? List.of(emptyPrimaryType)
            : primaryTypes;
    List<T> distinctTypes = distinctTypesInOrder(validationTypes);
    preValidate(distinctTypes, paths, representation);

    return new Traversal(
            representation, primarySnapshot, primaryTypes, primaryResources, allowIdentitylessRoots)
        .run();
  }

  private static <T> List<T> distinctTypesInOrder(List<T> primaryTypes) {
    Set<T> seen = new LinkedHashSet<>();
    List<T> types = new ArrayList<>();
    for (T type : primaryTypes) {
      if (seen.add(type)) {
        types.add(type);
      }
    }
    return types;
  }

  private void preValidate(
      List<T> distinctTypes, List<IncludePath> paths, EffectiveRepresentation representation) {
    for (IncludePath path : paths) {
      if (path.segments().size() > representation.policy().maxIncludeDepth()) {
        Class<?> resourceClass =
            distinctTypes.isEmpty() ? null : backend.rawClass(distinctTypes.getFirst());
        // Include-path specification failures have no document member location; the dotted path
        // stays in the message per the mapping-location contract.
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED,
            resourceClass,
            "Include path exceeds maxIncludeDepth "
                + representation.policy().maxIncludeDepth()
                + ": "
                + path.dotted());
      }
      for (T resourceType : distinctTypes) {
        validatePathAgainstType(path, resourceType, representation);
      }
    }
  }

  private void validatePathAgainstType(
      IncludePath path, T resourceType, EffectiveRepresentation representation) {
    T currentType = resourceType;
    IncludePolicy policy = representation.policy().includePolicy();
    for (int i = 0; i < path.segments().size(); i++) {
      String segment = path.segments().get(i);
      String dottedThrough = path.dottedThrough(i);
      String mappedResourceType = backend.resourceType(currentType);
      if (!backend.hasRelationship(currentType, segment)) {
        throw unknownRelationship(currentType, mappedResourceType, segment, dottedThrough);
      }
      if (!policy.allows(mappedResourceType, segment)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE,
            backend.rawClass(currentType),
            "Include denied for "
                + mappedResourceType
                + "."
                + segment
                + INCLUDE_PATH_CONTEXT
                + dottedThrough
                + "'");
      }
      currentType = backend.relatedType(currentType, segment, dottedThrough);
    }
  }

  private JsonApiMappingException unknownRelationship(
      T ownerType, String resourceType, String segment, String dottedThrough) {
    return JsonApiMappingException.withoutLocation(
        MappingDiagnostic.INVALID_INCLUDE_PATH,
        backend.rawClass(ownerType),
        "Unknown relationship '"
            + segment
            + "' on "
            + resourceType
            + INCLUDE_PATH_CONTEXT
            + dottedThrough
            + "'");
  }

  private final class Traversal {
    private final EffectiveRepresentation representation;
    private final List<?> primarySnapshot;
    private final List<T> primaryTypes;
    private final List<ResourceObject> primaryResources;
    private final boolean allowIdentitylessRoots;
    private final CompoundInclusionState state;
    private final Set<VisitKey<T>> visited = new HashSet<>();

    Traversal(
        EffectiveRepresentation representation,
        List<?> primarySnapshot,
        List<T> primaryTypes,
        List<ResourceObject> primaryResources,
        boolean allowIdentitylessRoots) {
      this.representation = representation;
      this.primarySnapshot = primarySnapshot;
      this.primaryTypes = primaryTypes;
      this.primaryResources = primaryResources;
      this.allowIdentitylessRoots = allowIdentitylessRoots;
      this.state = new CompoundInclusionState(representation.policy());
    }

    IncludedResourcesResult run() {
      for (ResourceObject primary : primaryResources) {
        state.registerPrimary(primary);
      }

      List<IncludePath> paths = representation.selection().includePaths();
      for (int primaryIndex = 0; primaryIndex < primarySnapshot.size(); primaryIndex++) {
        Object primaryDomain = primarySnapshot.get(primaryIndex);
        T primaryType = primaryTypes.get(primaryIndex);
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
          walkPath(primaryDomain, primaryType, paths.get(pathIndex), pathIndex);
        }
      }
      return state.result();
    }

    private void walkPath(Object primaryDomain, T primaryType, IncludePath path, int pathIndex) {
      Queue<DomainAtSegment<T>> queue = new ArrayDeque<>();
      queue.add(new DomainAtSegment<>(primaryDomain, primaryType, 0));
      while (!queue.isEmpty()) {
        processSegment(queue.remove(), path, pathIndex, queue);
      }
    }

    private void processSegment(
        DomainAtSegment<T> current,
        IncludePath path,
        int pathIndex,
        Queue<DomainAtSegment<T>> queue) {
      if (current.segmentIndex() >= path.segments().size()) {
        return;
      }
      Object domain = current.domain();
      T declaredType = current.declaredType();
      if (!isLenientRoot(domain, declaredType, current.segmentIndex())) {
        ResourceIdentity identity = identityOf(domain, declaredType);
        if (identity == null) {
          return;
        }
        VisitKey<T> visitKey =
            new VisitKey<>(identity, declaredType, pathIndex, current.segmentIndex());
        if (!visited.add(visitKey)) {
          return;
        }
      }

      String segment = path.segments().get(current.segmentIndex());
      String mappedResourceType = backend.resourceType(declaredType);
      String dottedThrough = path.dottedThrough(current.segmentIndex());
      if (!backend.hasRelationship(declaredType, segment)) {
        throw unknownRelationship(declaredType, mappedResourceType, segment, dottedThrough);
      }
      if (!representation.policy().includePolicy().allows(mappedResourceType, segment)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE,
            domain.getClass(),
            "Include denied for "
                + mappedResourceType
                + "."
                + segment
                + INCLUDE_PATH_CONTEXT
                + dottedThrough
                + "'");
      }

      List<Object> related = backend.relatedDomainObjects(domain, declaredType, segment);
      T relatedType = backend.relatedType(declaredType, segment, dottedThrough);
      int nextSegment = current.segmentIndex() + 1;
      boolean lastSegment = nextSegment >= path.segments().size();
      // When the owning resource's fieldset omits this segment, the traversed relationship is
      // absent from its wire representation while inclusion still follows it; resources reached
      // through such an edge legitimately lack inbound linkage in the produced document.
      List<String> ownerFields = representation.fieldsFor(mappedResourceType);
      boolean edgeOmittedByFieldset = ownerFields != null && !ownerFields.contains(segment);
      for (Object relatedDomain : related) {
        processRelated(
            relatedDomain,
            relatedType,
            edgeOmittedByFieldset,
            nextSegment,
            lastSegment,
            dottedThrough,
            queue);
      }
    }

    /** Handles one related domain object reached through the relationship of one path segment. */
    private void processRelated(
        Object relatedDomain,
        T relatedType,
        boolean edgeOmittedByFieldset,
        int nextSegment,
        boolean lastSegment,
        String propertyPath,
        Queue<DomainAtSegment<T>> queue) {
      T effectiveRelatedType = backend.effectiveType(relatedDomain, relatedType);
      ResourceIdentifier relatedIdentifier =
          backend.identifier(relatedDomain, effectiveRelatedType);
      // A related occurrence matching a primary under any id/lid alias IS the primary resource;
      // emitting it again would duplicate an identity core validation canonicalizes.
      if (state.matchesPrimary(relatedIdentifier)) {
        enqueueNextSegment(relatedDomain, effectiveRelatedType, nextSegment, lastSegment, queue);
        return;
      }
      if (edgeOmittedByFieldset) {
        state.addLinkageExemption(relatedIdentifier);
      }
      ResourceObject relatedResource =
          backend.render(relatedDomain, effectiveRelatedType, representation);
      state.offerIncluded(relatedResource, propertyPath);
      enqueueNextSegment(relatedDomain, effectiveRelatedType, nextSegment, lastSegment, queue);
    }

    private void enqueueNextSegment(
        Object domain,
        T declaredType,
        int nextSegment,
        boolean lastSegment,
        Queue<DomainAtSegment<T>> queue) {
      if (!lastSegment) {
        queue.add(new DomainAtSegment<>(domain, declaredType, nextSegment));
      }
    }

    private @Nullable ResourceIdentity identityOf(Object domain, T declaredType) {
      return state.preferredIdentity(backend.identifier(domain, declaredType));
    }

    /**
     * Returns {@code true} for a create-request primary root carrying no wire identity. Only the
     * traversal roots (segment zero) qualify, and only when the invocation allows identity-less
     * roots; such a root is enqueued exactly once per path, so visit-key dedup is vacuous for it.
     * Present-but-unconvertible identity values still fail through the backend exactly as on the
     * strict path.
     */
    private boolean isLenientRoot(Object domain, T declaredType, int segmentIndex) {
      return allowIdentitylessRoots
          && segmentIndex == 0
          && !backend.hasIdentity(domain, declaredType);
    }
  }

  private record DomainAtSegment<T>(Object domain, T declaredType, int segmentIndex) {}

  private record VisitKey<T>(
      ResourceIdentity identity, T declaredType, int pathIndex, int segmentIndex) {}
}
