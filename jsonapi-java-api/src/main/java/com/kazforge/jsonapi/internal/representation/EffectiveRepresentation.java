package com.kazforge.jsonapi.internal.representation;

import com.kazforge.jsonapi.representation.RepresentationPolicy;
import com.kazforge.jsonapi.representation.RepresentationSelection;
import java.util.Objects;

/** Internal composition of a caller's neutral representation selection and policy. */
public record EffectiveRepresentation(
    RepresentationSelection selection, RepresentationPolicy policy) {

  public EffectiveRepresentation {
    Objects.requireNonNull(selection, "selection");
    Objects.requireNonNull(policy, "policy");
  }
}
