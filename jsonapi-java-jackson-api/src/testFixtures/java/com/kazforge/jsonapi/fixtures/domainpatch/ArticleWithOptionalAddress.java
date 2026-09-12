package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Optional;

/**
 * Shared low-level PATCH DTO whose ordinary structured attribute is {@code Optional}-wrapped,
 * proving the transparent {@code Optional} qualification wrapper (ADR-014).
 */
@JsonApiResource(type = "articles")
public record ArticleWithOptionalAddress(
    @JsonApiId String id, @JsonApiAttribute Optional<Address> address) {}
