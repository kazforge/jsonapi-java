package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Shared low-level PATCH DTO with an ordinary structured {@link Dimensions} attribute. */
@JsonApiResource(type = "articles")
public record ArticleWithDimensions(
    @JsonApiId String id, @JsonApiAttribute Dimensions dimensions) {}
