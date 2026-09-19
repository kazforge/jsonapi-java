package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy;
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection;
import java.util.Objects;

/** Internal composition of neutral representation selection and policy for mapping operations. */
public record MappingRepresentation(
    RepresentationSelection selection, RepresentationPolicy policy) {

  public MappingRepresentation {
    Objects.requireNonNull(selection, "selection");
    Objects.requireNonNull(policy, "policy");
  }
}
