package com.kazforge.jsonapi.jackson.mapping;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.validation.ValidationContext;
import java.util.Objects;
import java.util.Set;

/**
 * Domain-mapping result that carries the produced document plus the identities of included
 * resources whose inbound linkage was removed by an applied sparse fieldset during that mapping
 * call.
 *
 * <p>Those sparse-fieldset linkage exemptions are mapping provenance, not caller state: pass the
 * whole {@code MappedDocument} to a JSON:API document writer and the writer composes its bound
 * {@link ValidationContext} with this provenance before validating and emitting. An empty set means
 * the mapped document carries no linkage exception and validates like any ordinary document.
 */
public record MappedDocument(
    JsonApiDocument document, Set<ResourceIdentity> sparseFieldsetLinkageExemptions) {

  public MappedDocument {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(sparseFieldsetLinkageExemptions, "sparseFieldsetLinkageExemptions");
    for (ResourceIdentity identity : sparseFieldsetLinkageExemptions) {
      Objects.requireNonNull(identity, "sparseFieldsetLinkageExemptions element");
    }
    sparseFieldsetLinkageExemptions = Set.copyOf(sparseFieldsetLinkageExemptions);
  }
}
