package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Optional;

/**
 * Shared direct typed PATCH DTO whose structured attribute is an {@code Optional}-wrapped
 * presence-aware shape, proving typed {@code Optional} unwrap/rewrap semantics (ADR-014).
 */
@JsonApiResource(type = "articles")
public record ArticleWithOptionalAddressPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<Optional<AddressPatch>> address) {}
