package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.representation.RepresentationPolicy;
import com.kazforge.jsonapi.representation.RepresentationSelection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal composition of a caller's neutral representation selection and policy. */
public record EffectiveRepresentation(
    RepresentationSelection selection, RepresentationPolicy policy) {

  public EffectiveRepresentation {
    Objects.requireNonNull(selection, "selection");
    Objects.requireNonNull(policy, "policy");
  }

  /**
   * Resolves the fieldset list for {@code resourceType}: {@code null} when the type key is absent
   * (unrestricted), otherwise the stored list (possibly empty, selecting no attributes or
   * relationships). Selective rendering and inclusion-traversal omission checks share this one
   * interpretation of absent versus present-empty fieldsets.
   */
  public @Nullable List<String> fieldsFor(String resourceType) {
    Objects.requireNonNull(resourceType, "resourceType");
    Map<String, List<String>> fieldsets = selection.fieldsets();
    if (!fieldsets.containsKey(resourceType)) {
      return null;
    }
    return fieldsets.get(resourceType);
  }
}
