package com.kazforge.jsonapi.mapping.contract;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import java.util.Set;

/** Neutral result of a representation-aware mapping contract operation. */
public record MappingContractResult(
    JsonApiDocument document, Set<ResourceIdentity> sparseFieldsetLinkageExemptions) {

  public MappingContractResult {
    sparseFieldsetLinkageExemptions = Set.copyOf(sparseFieldsetLinkageExemptions);
  }
}
