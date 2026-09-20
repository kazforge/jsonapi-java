package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Included resources plus fieldset-induced full-linkage exemptions for one mapping operation. */
public record MappingIncludedResult(
    @Nullable List<ResourceObject> included,
    Set<ResourceIdentity> sparseFieldsetLinkageExemptions) {

  public MappingIncludedResult {
    Objects.requireNonNull(sparseFieldsetLinkageExemptions, "sparseFieldsetLinkageExemptions");
    sparseFieldsetLinkageExemptions = Set.copyOf(sparseFieldsetLinkageExemptions);
  }
}
