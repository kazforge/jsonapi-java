package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Shared low-level PATCH DTO with a {@code Set}/{@code array}/{@code Map} structured attribute. */
@JsonApiResource(type = "articles")
public record ArticleWithContainerAddress(
    @JsonApiId String id, @JsonApiAttribute AddressWithContainers address) {}
