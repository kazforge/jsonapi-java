package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Shared low-level PATCH DTO with a multi-level ordinary structured {@link AddressWithGeo}
 * attribute.
 */
@JsonApiResource(type = "articles")
public record ArticleWithGeoAddress(
    @JsonApiId String id, @JsonApiAttribute AddressWithGeo address) {}
