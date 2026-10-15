package com.kazforge.jsonapi.fixtures.localid;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/**
 * Passive, application-shaped carrier declaring both independent identity roles: {@code @JsonApiId}
 * for the resource {@code id} member and {@code @JsonApiLocalId} for the {@code lid} member. Either
 * value may be null so the same shape expresses id-only, lid-only, and id+lid states.
 */
@JsonApiResource(type = "articles")
public record LocalIdentityArticle(
    @JsonApiId @Nullable String id,
    @JsonApiLocalId @Nullable String localId,
    @JsonApiAttribute @Nullable String title) {}
