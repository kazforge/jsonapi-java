package io.github.kazemek.jsonapi.jackson.internal.representation;

import io.github.kazemek.jsonapi.core.model.ResourceIdentity;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Included resources plus identities whose inbound linkage was removed by an applied fieldset while
 * inclusion still traversed the linking relationship.
 */
public record IncludedResourcesResult(
    @Nullable List<ResourceObject> included,
    Set<ResourceIdentity> sparseFieldsetLinkageExemptions) {

  public IncludedResourcesResult {
    Objects.requireNonNull(sparseFieldsetLinkageExemptions, "sparseFieldsetLinkageExemptions");
    sparseFieldsetLinkageExemptions = Set.copyOf(sparseFieldsetLinkageExemptions);
  }
}
