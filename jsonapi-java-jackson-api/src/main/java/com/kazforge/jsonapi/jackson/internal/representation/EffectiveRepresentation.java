package com.kazforge.jsonapi.jackson.internal.representation;

import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy;
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection;
import java.util.Objects;

/** Internal composition of a caller's neutral representation selection and policy. */
public record EffectiveRepresentation(
    RepresentationSelection selection, RepresentationPolicy policy) {

  public EffectiveRepresentation {
    Objects.requireNonNull(selection, "selection");
    Objects.requireNonNull(policy, "policy");
  }
}
