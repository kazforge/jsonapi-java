package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.representation.IncludePath;
import com.kazforge.jsonapi.jackson.representation.IncludePolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral JSON:API compound-inclusion semantics.
 *
 * <p>This class intentionally knows nothing about Jackson. Mapper-specific type introspection,
 * property access, optional/collection handling, and configured value conversion stay behind
 * {@link InclusionMappingBackend}. The traversal, include policy, identity handling, sparse
 * fieldset behavior, ordering, and compound-document semantics live here once for every backend.
 *
 * <p>This is an internal proof-of-concept boundary for KAZ-137.
 */
public final class GenericCompoundInclusionEngine<T> {

  private static final String INCLUDE_PATH_CONTEXT = " in include path '";

  private final InclusionMappingBackend<T> backend;

  public GenericCompoundInclusionEngine(InclusionMappingBackend<T> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public MappingIncludedResult collectIncluded(
      List<?> primarySnapshot,
      List<T> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable T emptyPrimaryType,
      MappingRepresentation representation) {
    return collectIncluded(
        primarySnapshot, primaryTypes, primaryResources, emptyPrimaryType, representation, false);
  }

  public MappingIncludedResult collectIncluded(
      List<?> primarySnapshot,
      List<T> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable T emptyPrimaryType,
      MappingRepresentation representation,
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
      return new MappingIncludedResult(
          representation.selection().includeRequested() ? List.of() : null, Set.of());
    }

    List<T> validationTypes =
        primaryTypes.isEmpty() && emptyPrimaryType != null
            ? List.of(emptyPrimaryType)
            : primaryTypes;
    preValidate(distinctTypesInOrder(validationTypes), paths, representation);

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
      List<T> distinctTypes,
      List<IncludePath> paths,
      MappingRepresentation representation) {
    for (IncludePath path : paths) {
      if (path.segments().size() > representation.policy().maxIncludeDepth()) {
        Class<?> resourceClass =
            distinctTypes.isEmpty() ? null : backend.rawClass(distinctTypes.getFirst());
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
      IncludePath path, T resourceType, MappingRepresentation representation) {
    T currentType = resourceType;
    IncludePolicy policy = representation.policy().includePolicy();

    for (int i = 0; i < path.segments().size(); i++) {
      String segment = path.segments().get(i);
      String dottedThrough = path.dottedThrough(i);
      String mappedResourceType = backend.resourceType(currentType);

      Optional<T> relatedType = backend.relatedType(currentType, segment, dottedThrough);
      if (relatedType.isEmpty()) {
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
      currentType = relatedType.orElseThrow();
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

  private static @Nullable List<String> fieldsFor(
      MappingRepresentation representation, String resourceType) {
    Map<String, List<String>> fieldsets = representation.selection().fieldsets();
    return fieldsets.containsKey(resourceType) ? fieldsets.get(resourceType) : null;
  }

  private final class Traversal {
    private final MappingRepresentation representation;
    private final List<?> primarySnapshot;
    private final List<T> primaryTypes;
    private final List<ResourceObject> primaryResources;
    private final boolean allowIdentitylessRoots;
    private final MappingCompoundInclusionState state;
    private final Set<VisitKey<T>> visited = new HashSet<>();

    Traversal(
        MappingRepresentation representation,
        List<?> primarySnapshot,
        List<T> primaryTypes,
        List<ResourceObject> primaryResources,
        boolean allowIdentitylessRoots) {
      this.representation = representation;
      this.primarySnapshot = primarySnapshot;
      this.primaryTypes = primaryTypes;
      this.primaryResources = primaryResources;
      this.allowIdentitylessRoots = allowIdentitylessRoots;
      this.state = new MappingCompoundInclusionState(representation.policy());
    }

    MappingIncludedResult run() {
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
      String resourceType = backend.resourceType(declaredType);
      String propertyPath = path.dottedThrough(current.segmentIndex());
      Optional<T> relatedType = backend.relatedType(declaredType, segment, propertyPath);
      if (relatedType.isEmpty()) {
        throw unknownRelationship(declaredType, resourceType, segment, propertyPath);
      }
      if (!representation.policy().includePolicy().allows(resourceType, segment)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE,
            domain.getClass(),
            "Include denied for "
                + resourceType
                + "."
                + segment
                + INCLUDE_PATH_CONTEXT
                + propertyPath
                + "'");
      }

      List<String> ownerFields = fieldsFor(representation, resourceType);
      boolean edgeOmittedByFieldset = ownerFields != null && !ownerFields.contains(segment);
      int nextSegment = current.segmentIndex() + 1;
      boolean lastSegment = nextSegment >= path.segments().size();

      for (Object relatedDomain : backend.relatedValues(domain, declaredType, segment)) {
        processRelated(
            relatedDomain,
            relatedType.orElseThrow(),
            edgeOmittedByFieldset,
            nextSegment,
            lastSegment,
            propertyPath,
            queue);
      }
    }

    private void processRelated(
        Object relatedDomain,
        T relatedType,
        boolean edgeOmittedByFieldset,
        int nextSegment,
        boolean lastSegment,
        String propertyPath,
        Queue<DomainAtSegment<T>> queue) {
      T effectiveRelatedType = backend.effectiveType(relatedDomain, relatedType);
      ResourceIdentifier relatedIdentifier = backend.identifier(relatedDomain, effectiveRelatedType);

      if (state.matchesPrimary(relatedIdentifier)) {
        enqueueNextSegment(
            relatedDomain, effectiveRelatedType, nextSegment, lastSegment, queue);
        return;
      }
      if (edgeOmittedByFieldset) {
        state.addLinkageExemption(relatedIdentifier);
      }

      ResourceObject relatedResource =
          backend.render(relatedDomain, effectiveRelatedType, representation);
      state.offerIncluded(relatedResource, propertyPath);
      enqueueNextSegment(
          relatedDomain, effectiveRelatedType, nextSegment, lastSegment, queue);
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
