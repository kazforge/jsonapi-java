package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.internal.representation.EffectiveRepresentation;
import com.kazforge.jsonapi.jackson.internal.representation.IncludedResourcesResult;
import com.kazforge.jsonapi.mapping.internal.GenericCompoundInclusionEngine;
import com.kazforge.jsonapi.mapping.internal.MappingIncludedResult;
import com.kazforge.jsonapi.mapping.internal.MappingRepresentation;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;

/**
 * jackson3 facade for compound-document inclusion.
 *
 * <p>The JSON:API traversal semantics are backend-neutral; this adapter only binds the existing
 * jackson3 mapping implementation to that shared engine.
 */
public final class CompoundInclusionEngine {

  private final GenericCompoundInclusionEngine<JavaType> delegate;

  public CompoundInclusionEngine(DomainResourceWriter writer) {
    Objects.requireNonNull(writer, "writer");
    this.delegate =
        new GenericCompoundInclusionEngine<>(new Jackson3InclusionMappingBackend(writer));
  }

  public IncludedResourcesResult collectIncluded(
      List<?> primarySnapshot,
      List<JavaType> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable JavaType emptyPrimaryType,
      EffectiveRepresentation representation) {
    return adapt(
        delegate.collectIncluded(
            primarySnapshot,
            primaryTypes,
            primaryResources,
            emptyPrimaryType,
            new MappingRepresentation(representation.selection(), representation.policy())));
  }

  public IncludedResourcesResult collectIncluded(
      List<?> primarySnapshot,
      List<JavaType> primaryTypes,
      List<ResourceObject> primaryResources,
      @Nullable JavaType emptyPrimaryType,
      EffectiveRepresentation representation,
      boolean allowIdentitylessRoots) {
    return adapt(
        delegate.collectIncluded(
            primarySnapshot,
            primaryTypes,
            primaryResources,
            emptyPrimaryType,
            new MappingRepresentation(representation.selection(), representation.policy()),
            allowIdentitylessRoots));
  }

  private static IncludedResourcesResult adapt(MappingIncludedResult result) {
    return new IncludedResourcesResult(result.included(), result.sparseFieldsetLinkageExemptions());
  }
}
