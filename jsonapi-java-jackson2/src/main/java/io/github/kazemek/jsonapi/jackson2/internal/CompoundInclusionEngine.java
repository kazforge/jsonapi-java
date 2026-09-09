package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import io.github.kazemek.jsonapi.core.model.RelationshipData;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.core.model.ResourceIdentity;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.internal.representation.CompoundInclusionState;
import io.github.kazemek.jsonapi.jackson.internal.representation.EffectiveRepresentation;
import io.github.kazemek.jsonapi.jackson.internal.representation.IncludedResourcesResult;
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage;
import io.github.kazemek.jsonapi.jackson.representation.IncludePath;
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Pre-validates include paths and walks domain graphs for compound-document inclusion.
 *
 * <p>All visit, identity, and included-output state is allocated per {@link #collectIncluded}
 * invocation. The engine instance itself is immutable and safe to share. Included resources are
 * emitted through the fieldset-aware selective write path.
 */
public final class CompoundInclusionEngine {

  private static final String INCLUDE_PATH_CONTEXT = " in include path '";

  private final DomainResourceWriter writer;

  public CompoundInclusionEngine(DomainResourceWriter writer) {
    this.writer = Objects.requireNonNull(writer, "writer");
  }

  /**
   * Collects included resources for the given primary domain snapshot and context.
   *
   * @return included list {@code null} when no inclusion was requested (empty path list), plus the
   *     identities of included resources whose inbound linkage was removed by an applied fieldset
   *     while inclusion still traversed the linking relationship
   */
  public IncludedResourcesResult collectIncluded(
      List<?> primarySnapshot,
      List<JavaType> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable JavaType emptyPrimaryType,
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
   * @return included list {@code null} when no inclusion was requested (empty path list), plus the
   *     identities of included resources whose inbound linkage was removed by an applied fieldset
   *     while inclusion still traversed the linking relationship
   */
  public IncludedResourcesResult collectIncluded(
      List<?> primarySnapshot,
      List<JavaType> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable JavaType emptyPrimaryType,
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
      return new IncludedResourcesResult(null, Set.of());
    }

    List<JavaType> validationTypes =
        primaryTypes.isEmpty() && emptyPrimaryType != null
            ? List.of(emptyPrimaryType)
            : primaryTypes;
    List<JavaType> distinctTypes = distinctTypesInOrder(validationTypes);
    preValidate(distinctTypes, paths, representation);

    return new Traversal(
            representation, primarySnapshot, primaryTypes, primaryResources, allowIdentitylessRoots)
        .run();
  }

  private static List<JavaType> distinctTypesInOrder(List<JavaType> primaryTypes) {
    Set<JavaType> seen = new LinkedHashSet<>();
    List<JavaType> types = new ArrayList<>();
    for (JavaType type : primaryTypes) {
      if (seen.add(type)) {
        types.add(type);
      }
    }
    return types;
  }

  private void preValidate(
      List<JavaType> distinctTypes,
      List<IncludePath> paths,
      EffectiveRepresentation representation) {
    for (IncludePath path : paths) {
      if (path.segments().size() > representation.policy().maxIncludeDepth()) {
        Class<?> resourceClass =
            distinctTypes.isEmpty() ? null : distinctTypes.getFirst().getRawClass();
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
      for (JavaType resourceType : distinctTypes) {
        validatePathAgainstType(path, resourceType, representation);
      }
    }
  }

  private void validatePathAgainstType(
      IncludePath path, JavaType resourceType, EffectiveRepresentation representation) {
    JavaType currentType = resourceType;
    IncludePolicy policy = representation.policy().includePolicy();
    for (int i = 0; i < path.segments().size(); i++) {
      String segment = path.segments().get(i);
      String dottedThrough = path.dottedThrough(i);
      ResourceMapping mapping = writer.mappingFor(currentType);
      MappingProperty property = findRelationship(mapping, segment);
      if (property == null) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_INCLUDE_PATH,
            currentType.getRawClass(),
            "Unknown relationship '"
                + segment
                + "' on "
                + mapping.resourceType()
                + INCLUDE_PATH_CONTEXT
                + dottedThrough
                + "'");
      }
      if (!policy.allows(mapping.resourceType(), segment)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE,
            currentType.getRawClass(),
            "Include denied for "
                + mapping.resourceType()
                + "."
                + segment
                + INCLUDE_PATH_CONTEXT
                + dottedThrough
                + "'");
      }
      currentType = resolveRelatedDomainType(property, currentType, dottedThrough);
    }
  }

  private static @Nullable MappingProperty findRelationship(
      ResourceMapping mapping, String jsonapiName) {
    for (MappingProperty property : mapping.relationships()) {
      if (property.jsonapiName().equals(jsonapiName)) {
        return property;
      }
    }
    return null;
  }

  private static JavaType resolveRelatedDomainType(
      MappingProperty property, JavaType ownerType, String dottedThrough) {
    JavaType propertyType = property.accessor().getType();
    JavaType relatedType = unwrapOptionalType(propertyType);
    if (DomainResourceWriter.isToManyType(relatedType)) {
      JavaType contentType = DomainResourceWriter.resolveContentType(relatedType);
      if (contentType == null) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
            ownerType.getRawClass(),
            "Cannot resolve collection content type for include path '" + dottedThrough + "'");
      }
      relatedType = unwrapOptionalType(contentType);
    }
    JavaType linkageType = RelationshipLinkageSupport.linkageJavaType(relatedType);
    if (linkageType != null) {
      relatedType =
          RelationshipLinkageSupport.unwrapOptionalType(
              RelationshipLinkageSupport.linkageTargetType(linkageType));
    }
    return relatedType;
  }

  private static JavaType unwrapOptionalType(JavaType type) {
    if (type.isTypeOrSubTypeOf(Optional.class) && type.containedTypeCount() > 0) {
      return type.containedType(0);
    }
    return type;
  }

  private final class Traversal {
    private final EffectiveRepresentation representation;
    private final List<?> primarySnapshot;
    private final List<JavaType> primaryTypes;
    private final List<ResourceObject> primaryResources;
    private final boolean allowIdentitylessRoots;
    private final CompoundInclusionState state;
    private final Set<VisitKey> visited = new HashSet<>();

    Traversal(
        EffectiveRepresentation representation,
        List<?> primarySnapshot,
        List<JavaType> primaryTypes,
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
        JavaType primaryType = primaryTypes.get(primaryIndex);
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
          walkPath(primaryDomain, primaryType, paths.get(pathIndex), pathIndex);
        }
      }
      return state.result();
    }

    private void walkPath(
        Object primaryDomain, JavaType primaryType, IncludePath path, int pathIndex) {
      Queue<DomainAtSegment> queue = new ArrayDeque<>();
      queue.add(new DomainAtSegment(primaryDomain, primaryType, 0));
      while (!queue.isEmpty()) {
        DomainAtSegment current = queue.poll();
        processSegment(current, path, pathIndex, queue);
      }
    }

    private void processSegment(
        DomainAtSegment current, IncludePath path, int pathIndex, Queue<DomainAtSegment> queue) {
      if (current.segmentIndex() >= path.segments().size()) {
        return;
      }
      Object domain = current.domain();
      JavaType declaredType = current.declaredType();
      if (!isLenientRoot(domain, declaredType, current.segmentIndex())) {
        ResourceIdentity identity = identityOf(domain, declaredType);
        if (identity == null) {
          return;
        }
        VisitKey visitKey = new VisitKey(identity, declaredType, pathIndex, current.segmentIndex());
        if (!visited.add(visitKey)) {
          return;
        }
      }

      String segment = path.segments().get(current.segmentIndex());
      ResourceMapping mapping = writer.mappingFor(declaredType);
      MappingProperty property = findRelationship(mapping, segment);
      if (property == null) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_INCLUDE_PATH,
            declaredType.getRawClass(),
            "Unknown relationship '"
                + segment
                + "' on "
                + mapping.resourceType()
                + INCLUDE_PATH_CONTEXT
                + path.dottedThrough(current.segmentIndex())
                + "'");
      }
      if (!representation.policy().includePolicy().allows(mapping.resourceType(), segment)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE,
            domain.getClass(),
            "Include denied for "
                + mapping.resourceType()
                + "."
                + segment
                + INCLUDE_PATH_CONTEXT
                + path.dottedThrough(current.segmentIndex())
                + "'");
      }

      List<Object> related = readRelatedDomainObjects(domain, property);
      String propertyPath = path.dottedThrough(current.segmentIndex());
      JavaType relatedType = resolveRelatedDomainType(property, declaredType, propertyPath);
      int nextSegment = current.segmentIndex() + 1;
      boolean lastSegment = nextSegment >= path.segments().size();
      // When the owning resource's fieldset omits this segment, the traversed relationship is
      // absent from its wire representation while inclusion still follows it; resources reached
      // through such an edge legitimately lack inbound linkage in the produced document.
      List<String> ownerFields =
          DomainResourceWriter.fieldsFor(representation, mapping.resourceType());
      boolean edgeOmittedByFieldset = ownerFields != null && !ownerFields.contains(segment);
      for (Object relatedDomain : related) {
        processRelated(
            relatedDomain,
            relatedType,
            edgeOmittedByFieldset,
            nextSegment,
            lastSegment,
            propertyPath,
            queue);
      }
    }

    /** Handles one related domain object reached through the relationship of one path segment. */
    private void processRelated(
        Object relatedDomain,
        JavaType relatedType,
        boolean edgeOmittedByFieldset,
        int nextSegment,
        boolean lastSegment,
        String propertyPath,
        Queue<DomainAtSegment> queue) {
      JavaType effectiveRelatedType = writer.effectiveType(relatedDomain, relatedType);
      ResourceIdentifier relatedIdentifier =
          writer.extractIdentifier(relatedDomain, effectiveRelatedType);
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
          writer.toResource(relatedDomain, effectiveRelatedType, representation);
      state.offerIncluded(relatedResource, propertyPath);
      enqueueNextSegment(relatedDomain, effectiveRelatedType, nextSegment, lastSegment, queue);
    }

    private void enqueueNextSegment(
        Object domain,
        JavaType declaredType,
        int nextSegment,
        boolean lastSegment,
        Queue<DomainAtSegment> queue) {
      if (!lastSegment) {
        queue.add(new DomainAtSegment(domain, declaredType, nextSegment));
      }
    }

    private @Nullable ResourceIdentity identityOf(Object domain, JavaType declaredType) {
      return state.preferredIdentity(writer.extractIdentifier(domain, declaredType));
    }

    /**
     * Returns {@code true} for a create-request primary root carrying no wire identity. Only the
     * traversal roots (segment zero) qualify, and only when the invocation allows identity-less
     * roots; such a root is enqueued exactly once per path, so visit-key dedup is vacuous for it.
     * Present-but-unconvertible identity values still fail here exactly as on the strict path.
     */
    private boolean isLenientRoot(Object domain, JavaType declaredType, int segmentIndex) {
      if (!allowIdentitylessRoots || segmentIndex != 0) {
        return false;
      }
      ResourceMapping mapping = writer.mappingFor(declaredType);
      return writer.extractId(domain, mapping) == null
          && writer.extractLocalId(domain, mapping) == null;
    }

    private List<Object> readRelatedDomainObjects(Object domain, MappingProperty property) {
      Object raw = writer.readRelationshipValue(domain, property);
      Object value = DomainResourceWriter.unwrapOptional(raw);
      JavaType propertyType = unwrapOptionalType(property.accessor().getType());
      if (DomainResourceWriter.isToManyType(propertyType)) {
        if (value == null) {
          return List.of();
        }
        List<Object> items = DomainResourceWriter.convertToCollection(value);
        List<Object> domainObjects = new ArrayList<>();
        for (Object item : items) {
          Object unwrapped = DomainResourceWriter.unwrapOptional(item);
          if (unwrapped instanceof RelationshipLinkage<?, ?>(Object target, Object ignored)) {
            unwrapped = target;
          }
          if (isIncludableDomainObject(unwrapped)) {
            domainObjects.add(unwrapped);
          }
        }
        return domainObjects;
      }
      if (value instanceof RelationshipLinkage<?, ?>(Object target, Object ignored)) {
        value = target;
      }
      if (isIncludableDomainObject(value)) {
        return List.of(value);
      }
      return List.of();
    }

    private static boolean isIncludableDomainObject(@Nullable Object value) {
      return value != null
          && !(value instanceof ResourceIdentifier)
          && !(value instanceof RelationshipData)
          && !(value instanceof RelationshipLinkage<?, ?>);
    }
  }

  private record DomainAtSegment(Object domain, JavaType declaredType, int segmentIndex) {}

  private record VisitKey(
      ResourceIdentity identity, JavaType declaredType, int pathIndex, int segmentIndex) {}
}
